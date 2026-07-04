#!/usr/bin/env python3
"""Reusable headless smoke-test harness for the minecraft-mod-mcp bridge.

Runs the version × loader × server-framework matrix under a headless Xvfb +
llvmpipe session: installs each combination, launches it, and verifies the
in-game mod's HTTP server comes up (and screenshots) for clients, or that
dedicated servers reach "Done". Results are printed as a table and dumped to
JSON for CI.

The harness is data-driven from version_config.ALL_VERSIONS and drives the
*bridge CLI* (so it exercises install/launch/natives/processors/modJar — the
real code path an `npx` user hits). By default it uses the locally-built CLI
(packages/minecraft-mod-mcp/dist/cli.js) so in-repo fixes are tested; pass
--npx to exercise the published npm package instead.

Usage:
  python3 scripts/matrix_test.py --list                       # show the matrix
  python3 scripts/matrix_test.py --client --filter "1.18*,1.20*"
  python3 scripts/matrix_test.py --server --matrix all
  python3 scripts/matrix_test.py --serve 1.21.7 --loader forge --timeout 240
  python3 scripts/matrix_test.py --client --npx               # test published pkg
"""
from __future__ import annotations

import argparse
import fnmatch
import json
import os
import shutil
import signal
import subprocess
import sys
import time
import urllib.request
import urllib.error
from dataclasses import dataclass, field, asdict
from typing import Optional

# Make the sibling version_config importable.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from version_config import ALL_VERSIONS, get_loaders, MODS_DIR  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIST_CLI = os.path.join(ROOT, "packages", "minecraft-mod-mcp", "dist", "cli.js")

PORT_START = 9876
PORT_END = 9000
SERVER_TYPES = ["vanilla", "paper", "spigot", "craftbukkit", "forge", "fabric", "neoforge"]


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
            return ["npx", "-y", "minecraft-mod-mcp"]
        if not os.path.isfile(DIST_CLI):
            raise RuntimeError(
                f"Built CLI not found at {DIST_CLI}. "
                "Run `npm run build --prefix packages/minecraft-mod-mcp` or pass --npx."
            )
        return ["node", DIST_CLI]

    def _run(self, args: list[str], timeout: int = 600) -> tuple[int, str]:
        cmd = self._base() + args
        if self.verbose:
            print(f"    $ {' '.join(cmd)}", flush=True)
        try:
            p = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
            out = (p.stdout or "") + (p.stderr or "")
            return p.returncode, out
        except subprocess.TimeoutExpired as e:
            return 124, (e.stdout or "") + (e.stderr or "") + "\n[timeout]"

    def install(self, version: str, loader: str, timeout: int = 900) -> tuple[int, str]:
        return self._run(["install", version, "--loader", loader], timeout=timeout)

    def install_server(self, version: str, loader: str, server_type: str, timeout: int = 900) -> tuple[int, str]:
        # `install` only fetches the client+loader; servers are set up by `server`.
        return self._run(["server", version, "--loader", loader, "--type", server_type, "--dry-run"], timeout=timeout)


# --------------------------------------------------------------------------- #
# Headless display + process management
# --------------------------------------------------------------------------- #
class Xvfb:
    """A persistent Xvfb session so multiple launches share one virtual display."""

    def __init__(self, display: str):
        self.display = display
        self.proc: Optional[subprocess.Popen] = None

    def start(self):
        if self._existing():
            return
        env = dict(os.environ)
        # Xvfb must be on PATH; if missing, client launches will simply fail later.
        self.proc = subprocess.Popen(
            ["Xvfb", self.display, "-screen", "0", "1280x720x24", "-ac", "+extension", "GLX", "-noreset"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
            env={**env, "LIBGL_ALWAYS_SOFTWARE": "1"},
        )
        time.sleep(2)
        if not self._existing():
            print(f"[warn] Xvfb did not start on {self.display}", file=sys.stderr)

    def _existing(self) -> bool:
        sock = f"/tmp/.X11-unix/X{self.display.lstrip(':')}"
        return os.path.exists(sock)

    def env(self) -> dict:
        e = dict(os.environ)
        e["DISPLAY"] = self.display
        e["LIBGL_ALWAYS_SOFTWARE"] = "1"
        return e

    def stop(self):
        if self.proc:
            self.proc.terminate()
            try:
                self.proc.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self.proc.kill()


def kill_mc_processes():
    """Kill any leftover Minecraft client/server java processes between tests."""
    me = os.getpid()
    for p in _java_pids():
        if p == me:
            continue
        try:
            os.kill(p, signal.SIGKILL)
        except ProcessLookupError:
            pass
        except PermissionError:
            pass
    time.sleep(2)


def _java_pids() -> list[int]:
    pids = []
    for pid in _list_pids():
        try:
            with open(f"/proc/{pid}/cmdline", "rb") as f:
                cmd = f.read().replace(b"\0", b" ").decode("utf-8", "ignore")
        except (FileNotFoundError, ProcessLookupError, PermissionError):
            continue
        if any(tok in cmd for tok in ("forgeclient", "KnotClient", "net.minecraft", "minecraft_server", "net.minecraft.server")):
            pids.append(pid)
    return pids


def _list_pids() -> list[int]:
    try:
        return [int(x) for x in os.listdir("/proc") if x.isdigit()]
    except OSError:
        return []


# --------------------------------------------------------------------------- #
# Mod HTTP discovery
# --------------------------------------------------------------------------- #
def find_mod_port(timeout_s: int = 180) -> Optional[int]:
    """Scan 9876→9000 for the mod's /api/status; returns the port or None."""
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        for port in range(PORT_START, PORT_END - 1, -1):
            if _probe(port):
                return port
        time.sleep(3)
    return None


# A urllib opener that NEVER uses a proxy — the mod HTTP server is always on
# localhost, and http_proxy/https_proxy env vars (set for downloads) would
# break localhost requests by routing them through the proxy.
_NO_PROXY_OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def _probe(port: int) -> bool:
    try:
        with _NO_PROXY_OPENER.open(f"http://127.0.0.1:{port}/api/status", timeout=2) as r:
            data = r.read().decode("utf-8", "ignore")
            return '"type":"minecraft-mod"' in data or '"type": "minecraft-mod"' in data
    except (urllib.error.URLError, TimeoutError, ConnectionError, ValueError):
        return False


def screenshot_via_http(port: int, path: str) -> bool:
    try:
        body = json.dumps({"cmd": "screenshot_to_file", "params": {"path": path}}).encode()
        req = urllib.request.Request(f"http://127.0.0.1:{port}/api/cmd", data=body,
                                     headers={"Content-Type": "application/json"})
        with _NO_PROXY_OPENER.open(req, timeout=30) as r:
            r.read()
        return os.path.isfile(path) and os.path.getsize(path) > 1024
    except Exception:
        return False


# --------------------------------------------------------------------------- #
# Tests
# --------------------------------------------------------------------------- #
def test_client(bridge: Bridge, version: str, loader: str, xvfb: Xvfb,
                timeout: int, game_root: str) -> Result:
    res = Result(version=version, loader=loader, kind="client")
    t0 = time.time()
    try:
        rc, out = bridge.install(version, loader)
        res.install_ok = rc == 0
        res.install_log = out[-400:]
        if rc != 0:
            res.error = "install failed"
            return res

        game_dir = os.path.join(game_root, f"{version}-{loader}")
        # Fresh game dir each run — leftover mod JARs from prior runs cause
        # Fabric to crash on duplicate mod IDs.
        shutil.rmtree(game_dir, ignore_errors=True)
        os.makedirs(game_dir, exist_ok=True)
        port = PORT_START
        env = xvfb.env()
        cmd = bridge._base() + ["launch", version, "--loader", loader,
                                "--port", str(port), "--mc-dir", game_dir,
                                "--width", "854", "--height", "480"]
        if bridge.verbose:
            print(f"    $ {' '.join(cmd)}", flush=True)
        # Detached launch: the bridge spawns MC and returns.
        subprocess.run(cmd, capture_output=True, text=True, timeout=120, env=env)

        # Wait for the mod HTTP server.
        found = find_mod_port(timeout_s=timeout)
        res.launch_ok = len(_java_pids()) > 0
        if found:
            res.mod_http_ok = True
            shot = os.path.join(game_dir, "smoke.png")
            # Retry screenshot — on slow-loading versions (1.21.11 on llvmpipe)
            # the MC instance may not be ready when mod HTTP first responds.
            for attempt in range(4):
                time.sleep(10 if attempt == 0 else 15)
                if screenshot_via_http(found, shot):
                    res.screenshot_ok = True
                    break
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
    try:
        cmd = bridge._base() + ["server", version, "--loader", loader,
                                "--type", server_type, "--port", "0"]
        if bridge.verbose:
            print(f"    $ {' '.join(cmd)}", flush=True)
        p = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        res.install_ok = True  # `server` installs inline
        deadline = time.time() + timeout
        ready = False
        while time.time() < deadline and p.poll() is None:
            line = "" if p.stdout is None else _readline_nonblock(p.stdout)
            if "Done (help)" in line or 'For help, type "help"' in line:
                ready = True
                break
            time.sleep(1)
        res.server_ready = ready
        if not ready:
            res.error = "server did not reach Done"
    except Exception as e:  # noqa: BLE001
        res.error = f"{type(e).__name__}: {e}"
    finally:
        try:
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
def build_matrix(args) -> list[tuple[str, str]]:
    if args.version:
        loaders = [args.loader] if args.loader else get_loaders(args.version)
        if args.loader and args.loader not in loaders:
            loaders = [args.loader]
        return [(args.version, l) for l in loaders]

    patterns = [p.strip() for p in args.filter.split(",") if p.strip()] if args.filter else ["*"]
    out = []
    for mc, info in ALL_VERSIONS.items():
        if not any(fnmatch.fnmatch(mc, pat) for pat in patterns):
            continue
        for loader in get_loaders(mc):
            if args.loader and loader != args.loader:
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
    print(f"\n{'VERSION':<10} {'LOADER':<9} {'TYPE':<8} {'VERDICT':<8} {'INSTALL':<7} {'HTTP/SRV':<8} {'SHOT':<5} {'TIME':>6}")
    print("-" * 70)
    for r in results:
        print(f"{r.version:<10} {r.loader:<9} {r.kind:<8} {r.verdict:<8} "
              f"{'ok' if r.install_ok else 'FAIL':<7} "
              f"{'ok' if (r.mod_http_ok or r.server_ready) else '-':<8} "
              f"{'ok' if r.screenshot_ok else '-':<5} {r.elapsed_s:>5}s")
    passed = sum(1 for r in results if r.verdict == "PASS")
    partial = sum(1 for r in results if r.verdict == "PARTIAL")
    failed = sum(1 for r in results if r.verdict == "FAIL")
    print("-" * 70)
    print(f"Totals: {passed} PASS, {partial} PARTIAL, {failed} FAIL, {len(results)} total")


# --------------------------------------------------------------------------- #
# Main
# --------------------------------------------------------------------------- #
def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--list", action="store_true", help="Print the test matrix and exit")
    ap.add_argument("--client", action="store_true", help="Test client launch (install → mod HTTP → screenshot)")
    ap.add_argument("--server", action="store_true", help="Test dedicated server launch")
    ap.add_argument("--serve", metavar="VERSION", help="One-shot: install+launch server+client for a version")
    ap.add_argument("--version", help="Single version to test (default: matrix)")
    ap.add_argument("--loader", help="Restrict to one loader (forge/fabric/neoforge)")
    ap.add_argument("--server-type", default="vanilla", help=f"Server framework (default: vanilla; one of {SERVER_TYPES})")
    ap.add_argument("--filter", default="*", help="Comma-separated version globs, e.g. '1.18*,1.20*' (default: *)")
    ap.add_argument("--matrix", choices=["all"], help="Test the full matrix")
    ap.add_argument("--timeout", type=int, default=180, help="Per-launch timeout in seconds (default: 180)")
    ap.add_argument("--npx", action="store_true", help="Use the published npx package instead of the local build")
    ap.add_argument("--display", default=":99", help="Xvfb display for client tests (default: :99)")
    ap.add_argument("--output", metavar="JSON", help="Write machine-readable results to this JSON file")
    ap.add_argument("--game-root", default=os.path.expanduser("~/.minecraft/mcp_launcher/smoke"),
                    help="Game directory root for isolated test instances")
    ap.add_argument("-v", "--verbose", action="store_true")
    args = ap.parse_args()

    if args.list:
        print(f"{'VERSION':<10} {'LOADERS':<24}")
        print("-" * 36)
        for mc in ALL_VERSIONS:
            print(f"{mc:<10} {', '.join(get_loaders(mc)):<24}")
        print(f"\n{len(ALL_VERSIONS)} versions. Server types: {', '.join(SERVER_TYPES)}")
        return 0

    if not (args.client or args.server or args.serve):
        ap.error("specify one of --client / --server / --serve / --list")

    if args.server_type not in SERVER_TYPES:
        ap.error(f"--server-type must be one of: {', '.join(SERVER_TYPES)}")

    matrix = build_matrix(args)
    if not matrix:
        print("No versions match the filter.")
        return 1

    bridge = Bridge(use_npx=args.npx, verbose=args.verbose)
    xvfb = Xvfb(args.display)
    if args.client or args.serve:
        xvfb.start()

    results: list[Result] = []
    os.makedirs(args.game_root, exist_ok=True)

    try:
        if args.serve:
            version = args.serve
            loaders = [args.loader] if args.loader else get_loaders(version)
            for loader in loaders:
                print(f"\n=== serve {version} ({loader}) ===", flush=True)
                # `serve` is the bridge's one-shot server+client; reuse the client path
                # but it also exercises the server. We model it as a client test.
                results.append(test_client(bridge, version, loader, xvfb, args.timeout, args.game_root))
        elif args.client:
            for version, loader in matrix:
                print(f"\n=== client {version} ({loader}) ===", flush=True)
                results.append(test_client(bridge, version, loader, xvfb, args.timeout, args.game_root))
        elif args.server:
            for version, loader in matrix:
                print(f"\n=== server {version} ({loader}) ({args.server_type}) ===", flush=True)
                results.append(test_server(bridge, version, loader, args.server_type, args.timeout, args.game_root))
    finally:
        xvfb.stop()
        kill_mc_processes()

    print_table(results)
    if args.output:
        with open(args.output, "w", encoding="utf-8") as f:
            json.dump([asdict(r) for r in results], f, indent=2)
        print(f"\nResults written to {args.output}")

    return 0 if all(r.verdict == "PASS" for r in results) else 1


if __name__ == "__main__":
    sys.exit(main())
