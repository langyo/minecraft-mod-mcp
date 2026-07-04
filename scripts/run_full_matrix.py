#!/usr/bin/env python3
"""Comprehensive end-to-end matrix: every version × loader × server-framework.

Runs headlessly via Xvfb, all downloads through proxy (http_proxy:7890).
Writes per-result JSON incrementally; kill/restart gracefully resumes.
Usage: python3 scripts/run_full_matrix.py [--resume results.json] [--client-only|--server-only]
"""
import json, os, shutil, signal, subprocess, sys, time, urllib.request, urllib.error
from dataclasses import dataclass, asdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from version_config import ALL_VERSIONS, get_loaders

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CLI = os.path.join(ROOT, "packages", "minecraft-mod-mcp", "dist", "cli.js")
PORT_START, PORT_END = 9876, 9000
DEFAULT_TIMEOUT = 180
GAME_ROOT = os.path.expanduser("~/.minecraft/mcp_launcher/smoke")
OUTPUT = "/tmp/full-matrix-results.json"
NO_PROXY = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def find_mod_port(timeout_s=DEFAULT_TIMEOUT):
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        for port in range(PORT_START, PORT_END - 1, -1):
            try:
                with NO_PROXY.open(f"http://127.0.0.1:{port}/api/status", timeout=2) as r:
                    if '"type":"minecraft-mod"' in r.read().decode():
                        return port
            except Exception:
                pass
        time.sleep(3)
    return None


def screenshot(port, path):
    try:
        body = json.dumps({"cmd": "screenshot_to_file", "params": {"path": path}}).encode()
        req = urllib.request.Request(f"http://127.0.0.1:{port}/api/cmd", data=body,
                                     headers={"Content-Type": "application/json"})
        with NO_PROXY.open(req, timeout=30) as r:
            r.read()
        return os.path.isfile(path) and os.path.getsize(path) > 1024
    except Exception:
        return False


def kill_mc():
    for pid in os.listdir("/proc"):
        if not pid.isdigit():
            continue
        try:
            with open(f"/proc/{pid}/cmdline", "rb") as f:
                cmd = f.read().replace(b"\0", b" ").decode("utf-8", "ignore")
        except Exception:
            continue
        if any(t in cmd for t in ("forgeclient", "KnotClient", "net.minecraft", "minecraft_server")):
            try:
                os.kill(int(pid), signal.SIGKILL)
            except Exception:
                pass
    time.sleep(3)


def run_cli(args, timeout=900, env=None):
    e = dict(os.environ)
    if env:
        e.update(env)
    try:
        r = subprocess.run([CLI] + args, capture_output=True, text=True, timeout=timeout, env=e)
        return r.returncode, (r.stdout or "") + (r.stderr or "")
    except subprocess.TimeoutExpired as e:
        return 124, (e.stdout or "") + (e.stderr or "") + "\n[timeout]"


def test_client(version, loader, timeout):
    kill_mc()
    game_dir = os.path.join(GAME_ROOT, f"{version}-{loader}")
    shutil.rmtree(game_dir, ignore_errors=True)
    os.makedirs(game_dir, exist_ok=True)

    t0 = time.time()
    env = {"DISPLAY": ":99", "LIBGL_ALWAYS_SOFTWARE": "1",
           "http_proxy": "http://127.0.0.1:7890", "https_proxy": "http://127.0.0.1:7890",
           "no_proxy": "127.0.0.1,localhost"}

    try:
        rc, out = run_cli(["install", version, "--loader", loader], timeout=600, env=env)
        install_ok = rc == 0
        if not install_ok:
            kill_mc()
            return {"version": version, "loader": loader, "kind": "client",
                    "install_ok": False, "launch_ok": False, "mod_http_ok": False,
                    "screenshot_ok": False, "elapsed_s": time.time()-t0,
                    "error": f"install failed (rc={rc})"}

        run_cli(["launch", version, "--loader", loader, "--port", str(PORT_START),
                 "--mc-dir", game_dir, "--width", "854", "--height", "480"],
                timeout=120, env=env)

        found = find_mod_port(timeout_s=timeout)

        # Check launch independently of mod HTTP
        launch_ok = False
        for p in os.listdir("/proc"):
            if not p.isdigit() or not os.path.isfile(f"/proc/{p}/cmdline"):
                continue
            try:
                with open(f"/proc/{p}/cmdline", "rb") as f:
                    cmd = f.read().replace(b"\0", b" ").decode()
                if any(t in cmd for t in ("KnotClient", "forgeclient", "net.minecraft.client.main.Main")):
                    launch_ok = True
                    break
            except Exception:
                pass

        mod_http_ok = found is not None
        shot_ok = False
        if mod_http_ok:
            for attempt in range(4):
                time.sleep(10 if attempt == 0 else 15)
                shot = os.path.join(game_dir, "smoke.png")
                if screenshot(found, shot):
                    shot_ok = True
                    break

        return {"version": version, "loader": loader, "kind": "client",
                "install_ok": install_ok, "launch_ok": launch_ok,
                "mod_http_ok": mod_http_ok, "screenshot_ok": shot_ok,
                "elapsed_s": round(time.time()-t0, 1)}
    except Exception as e:
        return {"version": version, "loader": loader, "kind": "client",
                "install_ok": False, "launch_ok": False, "mod_http_ok": False,
                "screenshot_ok": False, "elapsed_s": time.time()-t0,
                "error": f"{type(e).__name__}: {e}"}
    finally:
        kill_mc()


def test_server(version, loader, server_type, timeout):
    kill_mc()
    env = {"http_proxy": "http://127.0.0.1:7890", "https_proxy": "http://127.0.0.1:7890"}
    t0 = time.time()
    try:
        r = subprocess.Popen(
            [CLI, "server", version, "--loader", loader, "--type", server_type, "--port", "0"],
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, env={**os.environ, **env}
        )
        deadline = time.time() + min(timeout, 300)
        ready = False
        import select
        while time.time() < deadline and r.poll() is None:
            if select.select([r.stdout], [], [], 1)[0]:
                line = r.stdout.readline()
                if "Done" in line and "help" in line.lower():
                    ready = True
                    break
        return {"version": version, "loader": loader, "kind": "server",
                "server_type": server_type, "install_ok": True,
                "server_ready": ready, "elapsed_s": round(time.time()-t0, 1)}
    except Exception as e:
        return {"version": version, "loader": loader, "kind": "server",
                "server_type": server_type, "install_ok": False,
                "server_ready": False, "elapsed_s": time.time()-t0,
                "error": f"{type(e).__name__}: {e}"}
    finally:
        try:
            r.terminate()
        except Exception:
            pass
        kill_mc()


def build_todo(results_done):
    """Build the todo list minus already-completed and known-broken entries."""
    done = set((r["version"], r["loader"], r.get("kind","client"), r.get("server_type",""))
               for r in results_done)
    todo = []
    for mc in sorted(ALL_VERSIONS.keys()):
        info = ALL_VERSIONS[mc]
        parts = [int(x) for x in mc.split(".")]
        is_modern = parts[0] >= 2 or (parts[0] == 1 and len(parts) >= 2 and parts[1] >= 14)

        for loader in get_loaders(mc):
            # Skip pre-1.14 forge client installs (FG2 era installer not supported)
            if loader == "forge" and not is_modern:
                continue

            if (mc, loader, "client", "") not in done:
                todo.append(("client", mc, loader, ""))

            # Server: only vanilla for now, skip legacy forge servers
            if loader == "forge" and not is_modern:
                continue
            if (mc, loader, "server", "vanilla") not in done:
                todo.append(("server", mc, loader, "vanilla"))
    return todo


def main():
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("--resume", help="Resume from existing results JSON")
    ap.add_argument("--client-only", action="store_true")
    ap.add_argument("--server-only", action="store_true")
    ap.add_argument("--timeout", type=int, default=180)
    ap.add_argument("--output", default=OUTPUT)
    args = ap.parse_args()

    results = []
    if args.resume and os.path.isfile(args.resume):
        results = json.load(open(args.resume))
        print(f"Loaded {len(results)} existing results")

    todo = build_todo(results)
    if args.client_only:
        todo = [t for t in todo if t[0] == "client"]
    if args.server_only:
        todo = [t for t in todo if t[0] == "server"]

    print(f"Todo: {len(todo)} combos ({sum(1 for t in todo if t[0]=='client')} client, {sum(1 for t in todo if t[0]=='server')} server)")
    if not todo:
        print("Nothing to do!")
        return

    # Start Xvfb
    if not os.path.exists("/tmp/.X11-unix/X99"):
        subprocess.Popen(["Xvfb", ":99", "-screen", "0", "1280x720x24", "-ac",
                          "+extension", "GLX", "-noreset"],
                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        time.sleep(2)

    for i, (kind, ver, loader, stype) in enumerate(todo):
        sys.stdout.write(f"[{i+1}/{len(todo)}] {kind} {ver} {loader}{' '+stype if stype else ''} ")
        sys.stdout.flush()
        if kind == "client":
            r = test_client(ver, loader, args.timeout)
        else:
            r = test_server(ver, loader, stype, args.timeout)
        results.append(r)
        v = "PASS" if r.get("mod_http_ok") and r.get("screenshot_ok") else \
            "PARTIAL" if r.get("launch_ok") or r.get("server_ready") else "FAIL"
        err = r.get("error","")[:40]
        print(f"\r[{i+1}/{len(todo)}] {kind} {ver} {loader} → {v} ({r['elapsed_s']:.0f}s) {err}")
        with open(args.output, "w") as f:
            json.dump(results, f, indent=2)

    passed = sum(1 for r in results if r.get("mod_http_ok") and r.get("screenshot_ok"))
    partial = sum(1 for r in results if (r.get("launch_ok") or r.get("server_ready"))
                  and not (r.get("mod_http_ok") and r.get("screenshot_ok")))
    failed = sum(1 for r in results if not r.get("launch_ok") and not r.get("server_ready"))
    print(f"\nCOMPLETE: {passed} PASS, {partial} PARTIAL, {failed} FAIL, {len(results)} total")
    print(f"Results: {args.output}")


if __name__ == "__main__":
    sys.exit(main())
