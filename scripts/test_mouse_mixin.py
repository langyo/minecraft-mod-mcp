"""Automated MouseMixin validation per Fabric version.

For each version:
  1. Launch MC client+server via serve/npx CLI
  2. Wait for connection + enter MCP control mode
  3. Simulate REAL Windows API mouse movements (not MCP tool clicks)
  4. Verify player rotation stays stable (yaw/pitch unchanged)
  5. Report PASS/FAIL with details
  6. Kill all processes, cleanup

Usage:
  python scripts/test_mouse_mixin.py              # test all fabric versions
  python scripts/test_mouse_mixin.py --mc 1.21.11 # single version
  python scripts/test_mouse_mixin.py --dry-run   # just list versions, no launch
"""
import sys
import os
import time
import json
import subprocess
import signal
import argparse
import threading
from datetime import datetime

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from version_config import ALL_VERSIONS, MODS_DIR, get_loaders

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RESULTS_FILE = os.path.join(BASE, "test-mouse-results.json")

# --- Windows API mouse simulation ---
try:
    import ctypes
    from ctypes import wintypes

    user32 = ctypes.windll.user32
    MOUSEEVENTF_MOVE = 0x0001
    MOUSEEVENTF_LEFTDOWN = 0x0002
    MOUSEEVENTF_LEFTUP = 0x0004
    MOUSEEVENTF_ABSOLUTE = 0x8000

    def win_move_mouse(dx, dy):
        """Move mouse by relative delta (pixels)."""
        user32.mouse_event(MOUSEEVENTF_MOVE, dx, dy, 0, 0)

    def win_click():
        """Simulate left mouse click."""
        time.sleep(0.05)
        user32.mouse_event(MOUSEEVENTF_LEFTDOWN, 0, 0, 0, 0)
        time.sleep(0.05)
        user32.mouse_event(MOUSEEVENTF_LEFTUP, 0, 0, 0, 0)

    def win_get_cursor_pos():
        """Get current screen cursor position."""
        pt = wintypes.POINT()
        user32.GetCursorPos(ctypes.byref(pt))
        return pt.x, pt.y

    HAS_WIN_API = True
except Exception:
    HAS_WIN_API = False
    print("[WARN] Windows API not available, mouse simulation will be skipped")

# --- MCP HTTP API helpers ---
import urllib.request
import urllib.error


def _mcp_api(port, path, timeout=10):
    try:
        url = f"http://127.0.0.1:{port}{path}"
        req = urllib.request.Request(url)
        resp = urllib.request.urlopen(req, timeout=timeout)
        return json.loads(resp.read().decode())
    except Exception as e:
        return None


def mcp_ping(port):
    return _mcp_api(port, "/ping")


def mcp_player_info(port):
    return _mcp_api(port, "/player/info")


def mcp_enter_control_mode(port):
    try:
        url = f"http://127.0.0.1:{port}/control-mode/enter"
        data = json.dumps({}).encode()
        req = urllib.request.Request(url, data=data, method="POST")
        req.add_header("Content-Type", "application/json")
        resp = urllib.request.urlopen(req, timeout=10)
        return resp.status == 200
    except Exception as e:
        return False


def mcp_exit_control_mode(port):
    try:
        url = f"http://127.0.0.1:{port}/control-mode/exit"
        req = urllib.request.Request(url, method="POST")
        urllib.request.urlopen(req, timeout=5)
        return True
    except Exception:
        return False


def win_move_window_offscreen():
    """Move all Minecraft windows to off-screen coordinates."""
    if not HAS_WIN_API:
        return
    SWP_NOSIZE = 0x0001
    SWP_NOZORDER = 0x0004
    OFF_X = -32000
    OFF_Y = -32000
    buf = ctypes.create_unicode_buffer(256)

    def cb_enum(hwnd, _):
        length = user32.GetWindowTextW(hwnd, buf, 256)
        if length > 0 and "Minecraft" in buf.value:
            user32.SetWindowPos(hwnd, 0, OFF_X, OFF_Y, 0, 0,
                               SWP_NOSIZE | SWP_NOZORDER)
        return True

    WNDENUMPROC = ctypes.WINFUNCTYPE(ctypes.c_bool, ctypes.HWND, ctypes.LPARAM)
    user32.EnumWindows(WNDENUMPROC(cb_enum), 0)


def _find_mcp_port_in_log(log_path):
    """Extract MCP port from launch log."""
    try:
        with open(log_path, "r", errors="replace") as f:
            for line in f:
                if "MCP Port:" in line:
                    parts = line.split("MCP Port:")
                    if len(parts) >= 2:
                        return int(parts[1].strip())
    except Exception:
        pass
    return None


def wait_for_mcp(port, timeout=180, log_path=None):
    """Wait until MCP HTTP API responds. Tries log-extracted port then fallback."""
    start = time.time()
    
    # First try to find actual MCP port from launch log
    mcp_port = _find_mcp_port_in_log(log_path) if log_path else None
    
    # Try the passed-in port and the extracted port
    ports_to_try = [port]
    if mcp_port and mcp_port != port:
        ports_to_try.insert(0, mcp_port)
        print(f"    Found MCP port {mcp_port} in launch log")
    
    # Also scan nearby ports
    for p in range(9800, 10000):
        if p not in ports_to_try:
            ports_to_try.append(p)
    
    while time.time() - start < timeout:
        for p in ports_to_try[:20]:  # check first 20 candidates each round
            r = mcp_ping(p)
            if r:
                return p
        time.sleep(3)
    return None


def get_initial_rotation(port, samples=3):
    """Sample player rotation multiple times, return average."""
    yaws, pitches = [], []
    for _ in range(samples):
        info = mcp_player_info(port)
        if info and "rotation" in info:
            rot = info["rotation"]
            if isinstance(rot, dict):
                yaws.append(rot.get("yaw", 0))
                pitches.append(rot.get("pitch", 0))
            elif isinstance(rot, list) and len(rot) >= 2:
                yaws.append(rot[0])
                pitches.append(rot[1])
        time.sleep(0.3)
    if not yaws:
        return None, None
    return sum(yaws) / len(yaws), sum(pitches) / len(pitches)


def simulate_real_mouse_movement(duration_sec=8):
    """Use Windows API to move mouse like a real user would."""
    if not HAS_WIN_API:
        print("    [SKIP] No Windows API for mouse simulation")
        return

    # Save cursor position
    orig_x, orig_y = win_get_cursor_pos()
    print(f"    Saved cursor pos: ({orig_x}, {orig_y})")

    patterns = [
        # Rapid small jitter (like hand tremor)
        [(3, -2), (-2, 3), (1, -1), (-1, 2), (2, -3), (-3, 1)] * 15,
        # Slow sweep right
        [(5, 0)] * 30,
        # Slow sweep left
        [(-5, 0)] * 30,
        # Diagonal wiggle
        [(3, 3), (-3, -3), (3, -3), (-3, 3)] * 12,
        # Random-ish circular motion
        [(4, 0), (3, 3), (0, 4), (-3, 3), (-4, 0), (-3, -3), (0, -4), (3, -3)] * 4,
        # Fast flicks
        [(10, 5), (-8, -3), (6, -7), (-9, 4)] * 5,
    ]

    end_time = time.time() + duration_sec
    pattern_idx = 0
    step_idx = 0

    while time.time() < end_time:
        if pattern_idx >= len(patterns):
            break
        moves = patterns[pattern_idx]
        if step_idx >= len(moves):
            pattern_idx += 1
            step_idx = 0
            continue
        dx, dy = moves[step_idx]
        win_move_mouse(dx, dy)
        step_idx += 1
        time.sleep(0.02)

    # Also do a couple of clicks
    win_click()
    time.sleep(0.3)
    win_click()

    # Restore cursor position
    user32.SetCursorPos(orig_x, orig_y)


def kill_all_mc():
    """Kill all Java/MC processes."""
    for proc_name in ("java", "javaw"):
        try:
            subprocess.run(
                ["taskkill", "/F", "/IM", f"{proc_name}.exe"],
                capture_output=True, timeout=10,
            )
        except Exception:
            pass
    time.sleep(2)


def find_free_port(start=25565):
    import socket
    for port in range(start, start + 100):
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.settimeout(1)
            s.bind(("127.0.0.1", port))
            s.close()
            return port
        except OSError:
            continue
    return start


def launch_version(mc_version, port):
    """Launch MC client+server using minecraft-mod-mcp MCP tools."""
    # Use serve command but with explicit type=fabric for server
    npx_cmd = "npx"
    where_result = subprocess.run(
        ["where", "npx"], capture_output=True, text=True, timeout=5
    )
    if where_result.returncode == 0 and where_result.stdout.strip():
        npx_cmd = where_result.stdout.strip().split("\n")[0]

    args = [
        "minecraft-mod-mcp", "serve",
        "--version", mc_version,
        "--type", "fabric",
        "--loader", "fabric",
        "--port", str(port),
        "--width", "640",
        "--height", "480",
    ]

    env = os.environ.copy()
    env["PYTHONUNBUFFERED"] = "1"

    log_path = os.path.join(BASE, "test-launch.log")

    print(f"    Launching: {npx_cmd} {' '.join(args[:6])}...")
    if os.path.isfile(log_path):
        os.remove(log_path)
    with open(log_path, "w") as lf:
        proc = subprocess.Popen(
            [npx_cmd] + args,
            env=env,
            stdout=lf,
            stderr=subprocess.STDOUT,
            cwd=BASE,
            shell=True,
        )
    return proc, log_path


def test_one_version(mc_version, port):
    """Test one version. Returns result dict."""
    result = {
        "version": mc_version,
        "timestamp": datetime.now().isoformat(),
        "status": "unknown",
        "details": "",
        "rot_before": None,
        "rot_after": None,
        "delta_yaw": None,
        "delta_pitch": None,
    }

    print(f"\n{'='*60}")
    print(f"TESTING {mc_version}")
    print(f"{'='*60}")

    # Step 1: Kill any existing MC
    kill_all_mc()

    # Step 2: Launch
    proc, log_path = launch_version(mc_version, port)

    # Step 3: Wait for MCP API
    print(f"    Waiting for MCP on port {port}...")
    mcp_port = wait_for_mcp(port, timeout=180, log_path=log_path)
    if not mcp_port:
        result["status"] = "LAUNCH_FAIL"
        result["details"] = "MCP API did not respond within 180s"
        proc.kill()
        kill_all_mc()
        return result

    print(f"    Connected! (MCP port: {mcp_port})")
    
    # Move MC window off-screen so it doesn't interfere with user's desktop
    print(f"    Moving window off-screen...")
    win_move_window_offscreen()
 
    # Small delay for world load
    time.sleep(3)
 
    # Step 4: Enter control mode
    print(f"    Entering control mode...")
    entered = mcp_enter_control_mode(mcp_port)
    if not entered:
        result["status"] = "CONTROL_MODE_FAIL"
        result["details"] = "Could not enter control mode"
        mcp_exit_control_mode(mcp_port)
        proc.kill()
        kill_all_mc()
        return result

    time.sleep(1)

    # Step 5: Sample initial rotation
    print(f"    Sampling initial rotation...")
    init_yaw, init_pitch = get_initial_rotation(mcp_port, samples=3)
    if init_yaw is None:
        result["status"] = "ROTATION_READ_FAIL"
        result["details"] = "Could not read player rotation"
        mcp_exit_control_mode(mcp_port)
        proc.kill()
        kill_all_mc()
        return result

    result["rot_before"] = {"yaw": round(init_yaw, 2), "pitch": round(init_pitch, 2)}
    print(f"    Before: yaw={init_yaw:.2f}, pitch={init_pitch:.2f}")

    # Step 6: Simulate real mouse movement
    print(f"    Simulating 8s of real mouse movement via Windows API...")
    simulate_real_mouse_movement(duration_sec=8)

    # Step 7: Sample final rotation
    time.sleep(1)
    print(f"    Sampling final rotation...")
    final_yaw, final_pitch = get_initial_rotation(mcp_port, samples=3)
    if final_yaw is None:
        result["status"] = "ROTATION_READ_FAIL_AFTER"
        result["details"] = "Could not read final rotation"
        mcp_exit_control_mode(mcp_port)
        proc.kill()
        kill_all_mc()
        return result

    result["rot_after"] = {"yaw": round(final_yaw, 2), "pitch": round(final_pitch, 2)}
    d_yaw = abs(final_yaw - init_yaw)
    d_pitch = abs(final_pitch - init_pitch)
    result["delta_yaw"] = round(d_yaw, 2)
    result["delta_pitch"] = round(d_pitch, 2)
    print(f"    After:  yaw={final_yaw:.2f}, pitch={final_pitch:.2f}")
    print(f"    Delta:  yaw={d_yaw:.2f}, pitch={d_pitch:.2f}")

    # Step 8: Exit control mode
    mcp_exit_control_mode(mcp_port)
    time.sleep(1)

    # Step 9: Cleanup
    proc.kill()
    kill_all_mc()
    time.sleep(2)

    # Step 10: Determine pass/fail
    # Threshold: total rotation change < 2 degrees means suppression worked
    total_delta = d_yaw + d_pitch
    if total_delta < 2.0:
        result["status"] = "PASS"
        result["details"] = f"Rotation stable (total delta={total_delta:.2f} deg)"
    elif total_delta < 10.0:
        result["status"] = "WEAK"
        result["details"] = f"Partial suppression (total delta={total_delta:.2f} deg)"
    else:
        result["status"] = "FAIL"
        result["details"] = f"Rotation NOT suppressed (total delta={total_delta:.2f} deg)"

    tag = {"PASS": "PASS", "WEAK": "WEAK", "FAIL": "FAIL"}.get(result["status"], "?")
    print(f"\n    [{tag}] {mc_version}: delta=({d_yaw:.1f}, {d_pitch:.1f}) -> {result['status']}")

    return result


def main():
    parser = argparse.ArgumentParser(description="Automated MouseMixin validation")
    parser.add_argument("--mc", help="Test only this MC version")
    parser.add_argument("--dry-run", action="store_true", help="List versions without launching")
    parser.add_argument("--port", type=int, default=25565, help="Base MCP port")
    args = parser.parse_args()

    # Collect fabric versions to test
    versions_to_test = []
    for mc, info in sorted(ALL_VERSIONS.items()):
        if args.mc and mc != args.mc:
            continue
        if "fabric" not in get_loaders(mc):
            continue
        fabric_dir = os.path.join(MODS_DIR, mc, "fabric")
        mixin_file = os.path.join(
            fabric_dir, "src", "main", "java",
            "xyz", "langyo", "minecraft", "mcp", "mod", "mixin", "MouseMixin.java"
        )
        if os.path.isfile(mixin_file):
            versions_to_test.append((mc, info))

    print(f"Found {len(versions_to_test)} Fabric versions with MouseMixin:")
    for mc, _ in versions_to_test:
        print(f"  {mc}")

    if args.dry_run:
        print("\nDry run complete. No launches performed.")
        return

    if not HAS_WIN_API:
        print("\n[FATAL] Windows API not available. Cannot simulate mouse.")
        sys.exit(1)

    results = []
    base_port = args.port

    for idx, (mc, info) in enumerate(versions_to_test):
        port = base_port + idx
        try:
            r = test_one_version(mc, port)
            results.append(r)
        except KeyboardInterrupt:
            print("\n\nInterrupted! Cleaning up...")
            kill_all_mc()
            break
        except Exception as exc:
            results.append({
                "version": mc,
                "status": "CRASH",
                "details": str(exc)[:500],
                "timestamp": datetime.now().isoformat(),
            })
            print(f"    [CRASH] {mc}: {exc}")
            kill_all_mc()

    # Save results
    with open(RESULTS_FILE, "w") as f:
        json.dump(results, f, indent=2)

    # Summary
    print(f"\n{'='*60}")
    passes = sum(1 for r in results if r["status"] == "PASS")
    weaks = sum(1 for r in results if r["status"] == "WEAK")
    fails = sum(1 for r in results if r["status"] in ("FAIL", "CRASH", "LAUNCH_FAIL"))
    others = len(results) - passes - weaks - fails
    print(f"Results: {passes} PASS, {weaks} WEAK, {fails} FAIL, {others} OTHER / {len(results)} total")
    print(f"Report saved to {RESULTS_FILE}")
    print(f"{'='*60}")

    if fails > 0:
        print("\nFAILURES:")
        for r in results:
            if r["status"] in ("FAIL", "CRASH", "LAUNCH_FAIL"):
                print(f"  {r['version']}: [{r['status']}] {r.get('details', '')}")


if __name__ == "__main__":
    main()
