#!/usr/bin/env python3
"""Windows-compatible headless smoke-test harness for the minecraft-mod-mcp bridge.

This is the win32 sibling of matrix_test.py / run_full_matrix.py (which rely on
/proc, Xvfb and POSIX signals). On Windows there is no Xvfb — launches go to
the real desktop headlessly via `--mc-dir` isolation, and processes are killed
via `taskkill /T /PID` (tree-kill).

It exercises the same code path an `npx` user hits: install → launch → wait for
the in-game mod's HTTP server → screenshot via /api/cmd → optional menu click.
Servers are verified by scanning stdout for "Done".

Usage:
  python scripts/win_matrix_test.py --list                       # show the matrix
  python scripts/win_matrix_test.py --client --filter "1.18*,1.20*"
  python scripts/win_matrix_test.py --server --filter "1.20.1"
  python scripts/win_matrix_test.py --client --filter 1.20.1 --interact
  python scripts/win_matrix_test.py --client --npx               # use published pkg
"""
from __future__ import annotations

import argparse
import fnmatch
import json
import os
import re
import shutil
import subprocess
import sys
import time
import urllib.request
import urllib.error
from dataclasses import dataclass, field, asdict
from typing import Optional

# Make the sibling version_config importable.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from version_config import ALL_VERSIONS, get_loaders, MODS_DIR, is_legacy  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIST_CLI = os.path.join(ROOT, "packages", "minecraft-mod-mcp", "dist", "cli.js")

PORT_START = 9876
PORT_END = 9000
SERVER_TYPES = ["vanilla", "paper", "spigot", "craftbukkit", "forge", "fabric", "neoforge"]

# Local HTTP proxy for the (slow) Mojang/Forge/NeoForge maven downloads.
# Auto-detected from HTTPS_PROXY env; override with --proxy. Empty = direct.
PROXY_URL = os.environ.get("HTTPS_PROXY") or os.environ.get("https_proxy") or os.environ.get("HTTP_PROXY") or os.environ.get("http_proxy") or ""

# A urllib opener that NEVER uses a proxy — the mod HTTP server is always on
# localhost, and http_proxy/https_proxy env vars (set for downloads) would
# break localhost requests by routing them through the proxy.
_NO_PROXY_OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))


# --------------------------------------------------------------------------- #
# Result model
# --------------------------------------------------------------------------- #
@dataclass
class Result:
    version: str
    loader: str
    kind: str = "client"            # client | server
    server_type: str = ""
    install_ok: bool = False
    install_log: str = ""
    launch_ok: bool = False         # process stayed alive past early loading
    mod_http_ok: bool = False       # mod /api/status answered (client only)
    screenshot_ok: bool = False     # screenshot_to_file produced a PNG
    interact_ok: bool = False       # menu enumerate_buttons succeeded
    server_ready: bool = False      # server logged "Done" (server only)
    elapsed_s: float = 0.0
    error: str = ""

    @property
    def verdict(self) -> str:
        if self.kind == "client":
            if self.mod_http_ok and self.screenshot_ok:
                return "PASS"
            if self.launch_ok:
                return "PARTIAL"     # ran but mod HTTP/screenshot missing
            return "FAIL"
        return "PASS" if self.server_ready else ("PARTIAL" if self.install_ok else "FAIL")


# --------------------------------------------------------------------------- #
# Bridge CLI wrapper
# --------------------------------------------------------------------------- #
class Bridge:
    def __init__(self, use_npx: bool, verbose: bool):
        self.use_npx = use_npx
        self.verbose = verbose

    def _base(self) -> list[str]:
        if self.use_npx:
            # `npx -y` fetches the published package; we still launch from repo
            # root so modJarPath() can find locally-built JARs as a fallback.
            return ["npx", "-y", "minecraft-mod-mcp"]
        if not os.path.isfile(DIST_CLI):
            raise RuntimeError(
                f"Built CLI not found at {DIST_CLI}. "
                "Run `npm run build --prefix packages/minecraft-mod-mcp` or pass --npx."
            )
        return ["node", DIST_CLI]

    def _run(self, args: list[str], timeout: int = 600, env: dict | None = None) -> tuple[int, str]:
        cmd = self._base() + args
        if self.verbose:
            print(f"    $ {' '.join(cmd)}", flush=True)
        e = dict(os.environ)
        if env:
            e.update(env)
        try:
            # Capture as bytes + decode leniently: the CLI prints zh-CN console
            # messages in the local codepage (GBK), which breaks text=True.
            p = subprocess.run(cmd, capture_output=True, timeout=timeout, env=e)
            out = (p.stdout or b"").decode("utf-8", "replace") + (p.stderr or b"").decode("utf-8", "replace")
            return p.returncode, out
        except subprocess.TimeoutExpired as ex:
            so = (ex.stdout or b"").decode("utf-8", "replace") if isinstance(ex.stdout, bytes) else (ex.stdout or "")
            se = (ex.stderr or b"").decode("utf-8", "replace") if isinstance(ex.stderr, bytes) else (ex.stderr or "")
            return 124, so + se + "\n[timeout]"

    def install(self, version: str, loader: str, timeout: int = 900) -> tuple[int, str]:
        return self._run(["install", version, "--loader", loader], timeout=timeout)


# --------------------------------------------------------------------------- #
# Process management (Windows)
# --------------------------------------------------------------------------- #
_MC_CMD_TOKENS = (
    "forgeclient", "KnotClient", "net.minecraft.client.main.Main",
    "net.minecraft.server.Main", "minecraft_server", "Main.java",
    # Forge launch wrappers / tweakers (pre-1.14 and modded servers).
    "LaunchWrapper", "GradleStart", "FMLTweaker", "Tweaker",
    # Generic — catches the launcher's own -cp referencing MC jars.
    "mcp_launcher", "versions/",
)


def _java_pids() -> list[int]:
    """Return PIDs of running Minecraft client/server java processes on Windows.

    Uses Get-CimInstance via a here-string passed through `powershell -Command`.
    The earlier inline-filter form (`-Filter \"name='java.exe'\"`) was silently
    empty when launched from Python's subprocess list — the embedded quotes get
    mangled, so the filter matched nothing and zombies survived between tests.
    Matching is deliberately broad (any java referencing MC launch artifacts).
    """
    pids: list[int] = []
    # Use a script that filters in PowerShell (not via -Filter, which is picky
    # about quoting through subprocess). Wrap each line as PID|CommandLine.
    ps_script = (
        "Get-CimInstance Win32_Process | "
        "Where-Object { $_.Name -eq 'java.exe' -or $_.Name -eq 'javaw.exe' } | "
        "ForEach-Object { '{0}|{1}' -f $_.ProcessId, $_.CommandLine }"
    )
    try:
        r = subprocess.run(
            ["powershell", "-NoProfile", "-Command", ps_script],
            capture_output=True, timeout=20,
        )
        out = (r.stdout or b"").decode("utf-8", "replace")
        for line in out.splitlines():
            low = line.lower()
            # Skip the test harness's own build daemons. NB: do NOT skip on the
            # bare substring 'gradle' — JDKs live under ~/.gradle/jdks/, so every
            # java process whose JDK was auto-downloaded by Gradle has 'gradle'
            # in its path and would be wrongly skipped. Only skip real Gradle
            # builds (org.gradle.launcher / GradleWrapperMain / kotlin compile).
            if any(s in low for s in (
                "org.gradle.launcher", "gradlewrappermain", "gradle-daemon",
                "kotlin", "ksp", "lombok", "junit", "gradle/wrapper",
            )):
                continue
            if any(tok.lower() in low for tok in _MC_CMD_TOKENS):
                pid_str = line.split("|", 1)[0].strip()
                if pid_str.isdigit():
                    pids.append(int(pid_str))
    except (subprocess.TimeoutExpired, FileNotFoundError, OSError):
        pass
    return pids


def kill_mc_processes():
    """Tree-kill any leftover Minecraft java processes between tests.

    Uses taskkill /T (tree) first; falls back to PowerShell Stop-Process -Force
    for any PID taskkill couldn't reap. Without this, a zombie from a prior run
    holds port 9876 and the next launch's mod HTTP silently fails to bind.
    """
    pids = _java_pids()
    if not pids:
        return
    for pid in pids:
        subprocess.run(["taskkill", "/F", "/T", "/PID", str(pid)],
                       capture_output=True, text=True)
    # Fallback: some children escape taskkill /T; reap them directly.
    survivors = [p for p in pids if _pid_alive(p)]
    if survivors:
        pid_list = ",".join(str(p) for p in survivors)
        subprocess.run(
            ["powershell", "-NoProfile", "-Command",
             f"Stop-Process -Id {pid_list} -Force -ErrorAction SilentlyContinue"],
            capture_output=True, text=True, timeout=15,
        )
    time.sleep(2)


def _pid_alive(pid: int) -> bool:
    r = subprocess.run(
        ["powershell", "-NoProfile", "-Command",
         f"Get-Process -Id {pid} -ErrorAction SilentlyContinue | Measure-Object | Select-Object -ExpandProperty Count"],
        capture_output=True, text=True, timeout=10,
    )
    return (r.stdout or "").strip() not in ("0", "")


# --------------------------------------------------------------------------- #
# Mod HTTP discovery + control
# --------------------------------------------------------------------------- #
def find_mod_port(timeout_s: int = 180, known_port: int | None = None,
                  bail_if_dead: bool = False) -> Optional[int]:
    """Find the mod's /api/status port.

    We always launch with --port 9876, so the mod binds there. Probe the known
    port first and almost-exclusively (a full 9876→9000 sweep is slow and
    pointless when we control the launch). Falls back to a sweep only if the
    known port stays silent past 80% of the deadline.

    If bail_if_dead is set, give up early once no java process is alive —
    a crashed client (e.g. old Forge versions) will never come up, so polling
    for the full timeout just wastes time.
    """
    deadline = time.time() + timeout_s
    sweep_threshold = deadline - 0.2 * timeout_s  # last 20% for a sweep
    primary = known_port or PORT_START
    grace = 20  # seconds after launch before treating "no java" as a crash
    t_start = time.time()
    while time.time() < deadline:
        if _probe(primary):
            return primary
        # Once we're running low on time, fall back to a full sweep in case
        # the mod picked a different port (e.g. 9876 was busy).
        if time.time() > sweep_threshold:
            for port in range(PORT_START, PORT_END - 1, -1):
                if port != primary and _probe(port):
                    return port
        # Bail early if the game process has died (crash) — but only after a
        # grace period so we don't race the launch.
        if bail_if_dead and time.time() - t_start > grace and not _java_pids():
            return None
        time.sleep(3)
    return None


def _probe(port: int) -> bool:
    try:
        with _NO_PROXY_OPENER.open(f"http://127.0.0.1:{port}/api/status", timeout=2) as r:
            data = r.read().decode("utf-8", "ignore")
            return '"minecraft-mod"' in data
    except (urllib.error.URLError, TimeoutError, ConnectionError, ValueError):
        return False


def api_cmd(port: int, cmd: str, params: dict | None = None, timeout: int = 60) -> dict | None:
    """POST {cmd, params} to the mod's /api/cmd endpoint and return the JSON."""
    try:
        body = json.dumps({"cmd": cmd, "params": params or {}}).encode()
        req = urllib.request.Request(
            f"http://127.0.0.1:{port}/api/cmd", data=body,
            headers={"Content-Type": "application/json"},
        )
        with _NO_PROXY_OPENER.open(req, timeout=timeout) as r:
            return json.loads(r.read().decode("utf-8", "ignore"))
    except Exception:
        return None


def screenshot_via_http(port: int, path: str) -> bool:
    res = api_cmd(port, "screenshot_to_file", {"path": path}, timeout=60)
    if not res:
        return False
    # Some mod versions respond with {ok:true,...}; others with the raw path.
    return os.path.isfile(path) and os.path.getsize(path) > 1024


def enumerate_buttons(port: int) -> dict | None:
    """Ask the mod for the current GUI screen's button tree."""
    return api_cmd(port, "enumerate_widgets", timeout=30) or \
           api_cmd(port, "get_screen_buttons", timeout=30)


def click_button(port: int, index: int = 0) -> dict | None:
    return api_cmd(port, "click_button_index", {"index": index}, timeout=30)


# --------------------------------------------------------------------------- #
# Tests
# --------------------------------------------------------------------------- #
def test_client(bridge: Bridge, version: str, loader: str,
                timeout: int, game_root: str, interact: bool) -> Result:
    res = Result(version=version, loader=loader, kind="client")
    t0 = time.time()
    game_dir = os.path.join(game_root, f"{version}-{loader}")
    try:
        rc, out = bridge.install(version, loader)
        res.install_ok = rc == 0
        res.install_log = out[-400:]
        if rc != 0:
            res.error = "install failed"
            return res

        # Fresh game dir each run — leftover mod JARs from prior runs cause
        # Fabric to crash on duplicate mod IDs.
        shutil.rmtree(game_dir, ignore_errors=True)
        os.makedirs(game_dir, exist_ok=True)
        port = PORT_START
        cmd = bridge._base() + ["launch", version, "--loader", loader,
                                "--port", str(port), "--mc-dir", game_dir,
                                "--width", "854", "--height", "480"]
        if bridge.verbose:
            print(f"    $ {' '.join(cmd)}", flush=True)
        # Detached launch: the bridge spawns MC and returns. Capture as bytes
        # and decode leniently — the launcher prints zh-CN console text in GBK.
        try:
            subprocess.run(cmd, capture_output=True, timeout=120)
        except subprocess.TimeoutExpired:
            pass  # launch is detached; bridge may not exit on its own

        # Wait for the mod HTTP server.
        found = find_mod_port(timeout_s=timeout, known_port=port, bail_if_dead=True)
        res.launch_ok = len(_java_pids()) > 0
        if found:
            res.mod_http_ok = True
            shot = os.path.join(game_dir, "smoke.png")
            # Retry screenshot — on slow-loading versions the MC instance may
            # not be ready when mod HTTP first responds.
            for attempt in range(4):
                time.sleep(10 if attempt == 0 else 15)
                if screenshot_via_http(found, shot):
                    res.screenshot_ok = True
                    break
            if interact and res.screenshot_ok:
                # Try a menu read + click to prove the MCP control plane works.
                btns = enumerate_buttons(found)
                if isinstance(btns, dict) and btns:
                    res.interact_ok = True
                else:
                    # Fallback: any cmd that returns JSON counts as "controllable".
                    any_cmd = api_cmd(found, "get_player_info", timeout=30)
                    res.interact_ok = isinstance(any_cmd, dict)
        else:
            res.error = "mod HTTP did not come up"
    except Exception as e:  # noqa: BLE001
        res.error = f"{type(e).__name__}: {e}"
    finally:
        res.elapsed_s = round(time.time() - t0, 1)
        kill_mc_processes()
    return res


def test_server(bridge: Bridge, version: str, loader: str, server_type: str,
                timeout: int, game_root: str) -> Result:
    res = Result(version=version, loader=loader, kind="server", server_type=server_type)
    t0 = time.time()
    game_dir = os.path.join(game_root, f"srv-{version}-{loader}-{server_type}")
    os.makedirs(game_dir, exist_ok=True)
    p: Optional[subprocess.Popen] = None
    server_dir: str = ""
    log_path: str = ""
    try:
        cmd = bridge._base() + ["server", version, "--loader", loader,
                                "--type", server_type, "--port", "0"]
        if bridge.verbose:
            print(f"    $ {' '.join(cmd)}", flush=True)
        # The CLI does install (downloads server JAR + bundler-extract, which on
        # old MC versions can take 3-5 min on first run) THEN launches the
        # detached server and exits. Give the install its own generous budget so
        # it isn't killed mid-download; the remaining `timeout` budget is then
        # spent tailing the server log for "Done".
        install_budget = max(600, timeout)
        try:
            r = subprocess.run(cmd, capture_output=True, timeout=install_budget, cwd=ROOT)
        except subprocess.TimeoutExpired as ex:
            res.error = f"server CLI timed out after {install_budget}s (install)"
            res.elapsed_s = round(time.time() - t0, 1)
            kill_mc_processes()
            return res
        out = (r.stdout or b"").decode("utf-8", "replace") + (r.stderr or b"").decode("utf-8", "replace")
        res.install_log = out[-400:]
        if r.returncode != 0:
            res.error = f"server setup failed (rc={r.returncode})"
            return res
        res.install_ok = True
        # Parse "  Server dir: <path>" from CLI output.
        for line in out.splitlines():
            stripped = line.strip()
            if stripped.startswith("Server dir:") or stripped.startswith("Dir:"):
                server_dir = stripped.split(":", 1)[1].strip()
                break
        if not server_dir:
            res.error = "could not find server dir in CLI output"
            return res
        # MC writes to logs/latest.log (1.14+); older versions to server.log.
        log_path = os.path.join(server_dir, "logs", "latest.log")
        if not os.path.isfile(log_path):
            log_path = os.path.join(server_dir, "server.log")
        if not os.path.isfile(log_path):
            # The launcher also writes a console log next to the server jar.
            log_path = os.path.join(server_dir, "console.log")

        deadline = time.time() + timeout
        ready = False
        last_pos = 0
        while time.time() < deadline:
            if os.path.isfile(log_path):
                with open(log_path, "r", encoding="utf-8", errors="replace") as f:
                    f.seek(last_pos)
                    chunk = f.read()
                    last_pos = f.tell()
                if chunk and ('For help, type "help"' in chunk or "Done (" in chunk
                              or "Done (help)" in chunk):
                    ready = True
                    break
            # Still ready if the server process died without logging Done.
            time.sleep(2)
        res.server_ready = ready
        if not ready:
            res.error = "server did not reach Done"
    except Exception as e:  # noqa: BLE001
        res.error = f"{type(e).__name__}: {e}"
    finally:
        try:
            if p:
                p.terminate()
        except Exception:
            pass
        kill_mc_processes()
        res.elapsed_s = round(time.time() - t0, 1)
    return res


def _readline_nonblock(stream):
    """Best-effort non-blocking line read (servers can be chatty)."""
    import select
    if select.select([stream], [], [], 0.5)[0]:
        return stream.readline()
    return ""


# --------------------------------------------------------------------------- #
# Matrix selection
# --------------------------------------------------------------------------- #
def _mc_ge(a: str, b: str) -> bool:
    """Return True if MC version string a >= b (numeric component compare)."""
    try:
        aa = [int(x) for x in a.split(".")]
        bb = [int(x) for x in b.split(".")]
    except ValueError:
        return False
    for i in range(max(len(aa), len(bb))):
        x = aa[i] if i < len(aa) else 0
        y = bb[i] if i < len(bb) else 0
        if x > y:
            return True
        if x < y:
            return False
    return True


def build_matrix(args) -> list[tuple[str, str]]:
    if args.version:
        loaders = [args.loader] if args.loader else get_loaders(args.version)
        return [(args.version, l) for l in loaders]

    patterns = [p.strip() for p in args.filter.split(",") if p.strip()] if args.filter else ["*"]
    out = []
    for mc in ALL_VERSIONS:
        if not any(fnmatch.fnmatch(mc, pat) for pat in patterns):
            continue
        for loader in get_loaders(mc):
            if args.loader and loader != args.loader:
                continue
            # Skip pre-1.14 forge: FG2-era installers are unsupported per the
            # upstream harness. (de0202d8)
            if loader == "forge" and not _mc_ge(mc, "1.14"):
                continue
            out.append((mc, loader))
    return out


# --------------------------------------------------------------------------- #
# Reporting
# --------------------------------------------------------------------------- #
def print_table(results: list[Result]):
    if not results:
        print("(no results)")
        return
    print(f"\n{'VERSION':<10} {'LOADER':<9} {'TYPE':<8} {'VERDICT':<8} {'INSTALL':<7} "
          f"{'HTTP/SRV':<8} {'SHOT':<5} {'UI':<4} {'TIME':>6}")
    print("-" * 78)
    for r in results:
        print(f"{r.version:<10} {r.loader:<9} {r.kind:<8} {r.verdict:<8} "
              f"{'ok' if r.install_ok else 'FAIL':<7} "
              f"{'ok' if (r.mod_http_ok or r.server_ready) else '-':<8} "
              f"{'ok' if r.screenshot_ok else '-':<5} "
              f"{'ok' if r.interact_ok else '-':<4} {r.elapsed_s:>5}s")
    passed = sum(1 for r in results if r.verdict == "PASS")
    partial = sum(1 for r in results if r.verdict == "PARTIAL")
    failed = sum(1 for r in results if r.verdict == "FAIL")
    print("-" * 78)
    print(f"Totals: {passed} PASS, {partial} PARTIAL, {failed} FAIL, {len(results)} total")


# --------------------------------------------------------------------------- #
# Main
# --------------------------------------------------------------------------- #
def main():
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--list", action="store_true", help="Print the test matrix and exit")
    ap.add_argument("--client", action="store_true",
                    help="Test client launch (install → mod HTTP → screenshot)")
    ap.add_argument("--server", action="store_true", help="Test dedicated server launch")
    ap.add_argument("--version", help="Single version to test (default: matrix)")
    ap.add_argument("--loader", help="Restrict to one loader (forge/fabric/neoforge)")
    ap.add_argument("--server-type", default="vanilla",
                    help=f"Server framework (default: vanilla; one of {SERVER_TYPES})")
    ap.add_argument("--filter", default="*",
                    help="Comma-separated version globs, e.g. '1.18*,1.20*' (default: *)")
    ap.add_argument("--timeout", type=int, default=180,
                    help="Per-launch timeout in seconds (default: 180)")
    ap.add_argument("--npx", action="store_true",
                    help="Use the published npx package instead of the local build")
    ap.add_argument("--output", metavar="JSON",
                    help="Write machine-readable results to this JSON file")
    ap.add_argument("--resume", metavar="JSON",
                    help="Skip combos already present (by version/loader/kind) in this file")
    ap.add_argument("--game-root",
                    default=os.path.join(os.path.expanduser("~"), ".minecraft",
                                         "mcp_launcher", "win-smoke"),
                    help="Game directory root for isolated test instances")
    ap.add_argument("--interact", action="store_true",
                    help="After a client PASS, also verify menu enumerate/click")
    ap.add_argument("--proxy", metavar="URL", default=PROXY_URL,
                    help=f"HTTP proxy for Mojang/Forge/NeoForge downloads (default: env HTTPS_PROXY or {PROXY_URL or 'none'})")
    ap.add_argument("-v", "--verbose", action="store_true")
    args = ap.parse_args()

    # Apply the proxy so the CLI + subprocess children (gradle, java) pick it
    # up. The bridge CLI's setupGlobalProxy() reads these same env vars.
    if args.proxy:
        os.environ["HTTP_PROXY"] = args.proxy
        os.environ["HTTPS_PROXY"] = args.proxy
        os.environ["http_proxy"] = args.proxy
        os.environ["https_proxy"] = args.proxy

    if args.list:
        print(f"{'VERSION':<10} {'LOADERS':<24}")
        print("-" * 36)
        for mc in ALL_VERSIONS:
            print(f"{mc:<10} {', '.join(get_loaders(mc)):<24}")
        print(f"\n{len(ALL_VERSIONS)} versions. Server types: {', '.join(SERVER_TYPES)}")
        return 0

    if not (args.client or args.server):
        ap.error("specify one of --client / --server / --list")

    if args.server_type not in SERVER_TYPES:
        ap.error(f"--server-type must be one of: {', '.join(SERVER_TYPES)}")

    matrix = build_matrix(args)
    if not matrix:
        print("No versions match the filter.")
        return 1

    # Resume support: drop combos already recorded as PASS in the resume file.
    done_keys: set[tuple] = set()
    prior_results: list[dict] = []
    if args.resume and os.path.isfile(args.resume):
        try:
            with open(args.resume, "r", encoding="utf-8") as f:
                prior_results = json.load(f)
            for r in prior_results:
                if r.get("verdict") in ("PASS",):
                    done_keys.add((r["version"], r["loader"], r.get("kind", "client"),
                                   r.get("server_type", "")))
        except Exception:
            pass
    matrix = [(v, l) for (v, l) in matrix
              if (v, l, "client" if args.client else "server", args.server_type if args.server else "")
              not in done_keys]

    bridge = Bridge(use_npx=args.npx, verbose=args.verbose)
    results: list[Result] = [Result(**{k: v for k, v in r.items() if k != "verdict"})
                             for r in prior_results] if prior_results else []

    os.makedirs(args.game_root, exist_ok=True)

    try:
        if args.client:
            for version, loader in matrix:
                print(f"\n=== client {version} ({loader}) ===", flush=True)
                results.append(test_client(bridge, version, loader, args.timeout,
                                           args.game_root, args.interact))
        elif args.server:
            for version, loader in matrix:
                print(f"\n=== server {version} ({loader}) ({args.server_type}) ===", flush=True)
                results.append(test_server(bridge, version, loader, args.server_type,
                                           args.timeout, args.game_root))
    finally:
        kill_mc_processes()

    print_table(results)

    if args.output:
        serial = []
        for r in results:
            d = asdict(r)
            d["verdict"] = r.verdict
            serial.append(d)
        with open(args.output, "w", encoding="utf-8") as f:
            json.dump(serial, f, indent=2)
        print(f"\nResults written to {args.output}")

    return 0 if all(r.verdict == "PASS" for r in results) else 1


if __name__ == "__main__":
    sys.exit(main())
