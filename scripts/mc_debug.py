"""Launch MC, build mod, deploy, and run interactive debug loop.

Usage:
  python scripts/mc_debug.py                     # full cycle: build + launch + interactive
  python scripts/mc_debug.py --no-build          # skip build, just connect to running MC
  python scripts/mc_debug.py --auto              # auto-test: enter world + screenshot
  python scripts/mc_debug.py --auto-full         # full auto: build + launch + enter world + place sign
  python scripts/mc_debug.py --cmd "click 213 112"  # run one command and exit

Interactive commands (when no --cmd):
  ss [name]          Screenshot
  click X Y          Click at coordinates
  btn INDEX          Click button by index
  key KEY            Press key
  type TEXT          Type text
  chat TEXT          Open chat, type, enter
  cmd /command       Open chat, type /command, enter
  widgets            List screen widgets
  release            Release mouse grab
  place              Place block
  use                Use item
  wait SECONDS       Wait
  overlay            Toggle overlay
  quit               Exit
"""

import argparse
import os
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCRIPTS = ROOT / "scripts"
SS_DIR = ROOT / "screenshots" / "debug"
SS_DIR.mkdir(parents=True, exist_ok=True)

sys.path.insert(0, str(SCRIPTS))
from mc_http import McpClient


def kill_java():
    if sys.platform == "win32":
        os.system("taskkill /F /IM javaw.exe 2>nul")
        os.system("taskkill /F /IM java.exe 2>nul")
    else:
        os.system("pkill -f minecraft 2>/dev/null; pkill -f forge 2>/dev/null")


def build_mod():
    """Build common + forge mod, deploy to mods folder."""
    java_home = ""
    if sys.platform == "win32":
        java_home = r"C:\Program Files\Amazon Corretto\jdk21.0.8_9"
    else:
        java_home = "/usr/lib/jvm/java-21"
    if not Path(java_home).exists():
        java_home = os.environ.get("JAVA_HOME", java_home)

    env = os.environ.copy()
    env["JAVA_HOME"] = java_home

    gradlew_root = str(ROOT / ("gradlew.bat" if sys.platform == "win32" else "gradlew"))
    print("[BUILD] Common module...")
    r = subprocess.run(
        [gradlew_root, "--no-daemon", ":packages:common:jar", ":packages:common:publishToMavenLocal"],
        cwd=str(ROOT), capture_output=True, env=env, timeout=180)
    output = (r.stdout or b"").decode("utf-8", errors="replace") + (r.stderr or b"").decode("utf-8", errors="replace")
    if r.returncode != 0 or "BUILD FAILED" in output:
        print(f"[BUILD] FAILED (rc={r.returncode}):\n{output[-800:]}")
        return False

    forge_dir = ROOT / "packages" / "mods" / "1.21.7" / "forge"
    print("[BUILD] Forge mod...")
    gradlew_forge = str(forge_dir / ("gradlew.bat" if sys.platform == "win32" else "gradlew"))
    r = subprocess.run(
        [gradlew_forge, "--no-daemon", "clean", "jar"],
        cwd=str(forge_dir), capture_output=True, env=env, timeout=180)
    output = (r.stdout or b"").decode("utf-8", errors="replace") + (r.stderr or b"").decode("utf-8", errors="replace")
    if r.returncode != 0 or "BUILD FAILED" in output:
        print(f"[BUILD] FAILED (rc={r.returncode}):\n{output[-800:]}")
        return False

    jars = sorted((forge_dir / "build" / "libs").glob("*.jar"),
                  key=lambda f: f.stat().st_mtime, reverse=True)
    if not jars:
        print("[BUILD] No jar found")
        return False

    mod_jar = jars[0]
    print(f"[BUILD] Built: {mod_jar.name} ({mod_jar.stat().st_size // 1024}KB)")

    mods_dirs = [
        Path(os.environ.get("APPDATA", os.path.expanduser("~"))) / ".minecraft" / "mods",
        ROOT / ".minecraft" / "versions" / "1.21.7-forge-57.0.2" / "mods",
    ]
    for d in mods_dirs:
        d.mkdir(parents=True, exist_ok=True)
        dest = d / mod_jar.name
        import shutil
        shutil.copy2(mod_jar, dest)
        print(f"[DEPLOY] {dest}")

    return True


def launch_mc():
    """Launch MC via launch script. Returns subprocess.Popen."""
    script = SCRIPTS / "launch_mc.py"
    proc = subprocess.Popen(
        [sys.executable, str(script), "1.21.7-forge-57.0.2"],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )
    print(f"[LAUNCH] MC starting (pid={proc.pid})...")
    return proc


def wait_for_server(mc, timeout=120):
    """Wait for MC HTTP server to be ready."""
    mc_client = McpClient()
    deadline = time.time() + timeout
    while time.time() < deadline:
        if mc and mc.poll() is not None:
            print(f"[LAUNCH] MC exited with code {mc.returncode}")
            return None
        if mc_client.is_ready():
            print(f"[LAUNCH] Server ready!")
            return mc_client
        time.sleep(2)
    print("[LAUNCH] Timeout waiting for server")
    return None


def save_screenshot(png_bytes, name="debug"):
    ts = int(time.time())
    path = SS_DIR / f"{name}_{ts}.png"
    path.write_bytes(png_bytes)
    kb = len(png_bytes) // 1024
    print(f"  SS: {path} ({kb}KB)")
    return str(path)


def auto_create_redstone_world(mc: McpClient):
    """Create a new Redstone Ready superflat world from main menu."""
    import datetime

    # Step 1: Click Singleplayer
    print("[WORLD] Clicking Singleplayer...")
    mc.click_button(0)
    time.sleep(3)

    # Step 2: Click Create New World
    print("[WORLD] Clicking Create New World...")
    mc.click_button(0)
    time.sleep(3)

    # Step 3: Switch to World tab (index 1)
    print("[WORLD] Switching to World tab...")
    mc.cmd("switch_tab", tab=1)
    time.sleep(1)

    # Step 4: Cycle WorldType to 超平坦 (Superflat)
    print("[WORLD] Cycling world type to Superflat...")
    mc.click_button(3)
    time.sleep(0.5)

    # Step 5: Click 自定义 (Customize) button
    print("[WORLD] Clicking Customize...")
    mc.click_button(4)
    time.sleep(2)

    # Step 6: Click 预设 (Presets) button
    print("[WORLD] Clicking Presets...")
    mc.click_button(0)
    time.sleep(2)

    # Step 7: Select Redstone Ready preset (scroll up and click)
    print("[WORLD] Selecting Redstone Ready preset...")
    mc.cmd("scroll", clicks=-5)
    time.sleep(1)
    mc.click(200, 120)
    time.sleep(1)

    # Step 8: Click 使用预设 (Use Preset)
    print("[WORLD] Clicking Use Preset...")
    mc.click_button(1)
    time.sleep(1)

    # Step 9: Click 完成 (Done)
    print("[WORLD] Clicking Done...")
    mc.click_button(0)
    time.sleep(1)

    # Step 10: Switch back to Game tab (index 0)
    print("[WORLD] Switching to Game tab...")
    mc.cmd("switch_tab", tab=0)
    time.sleep(1)

    # Step 11: Set Creative mode (cycle 2 times)
    print("[WORLD] Setting Creative mode...")
    mc.click_button(5)
    time.sleep(0.5)
    mc.click_button(5)
    time.sleep(0.5)

    # Step 12: Click Create
    print("[WORLD] Clicking Create...")
    mc.click_button(1)
    time.sleep(25)

    print("[WORLD] Releasing mouse...")
    mc.release_mouse()
    time.sleep(2)

    png = mc.screenshot()
    if png:
        return save_screenshot(png, "redstone_world")
    return None


def auto_enter_world(mc: McpClient):
    """Auto-navigate: Singleplayer -> select world -> Play."""
    print("[AUTO] Clicking Singleplayer...")
    mc.click_button(0)
    time.sleep(3)

    print("[AUTO] Selecting world entry...")
    mc.click(213, 112)
    time.sleep(1)

    print("[AUTO] Clicking Play...")
    mc.click_button(1)
    time.sleep(20)

    print("[AUTO] Releasing mouse...")
    mc.release_mouse()
    time.sleep(1)

    print("[AUTO] Taking screenshot...")
    png = mc.screenshot()
    if png:
        return save_screenshot(png, "auto_world")
    return None


def auto_full_flow(mc: McpClient):
    """Full flow: enter world, set creative, place sign."""
    auto_enter_world(mc)
    time.sleep(2)

    print("[AUTO] Setting gamemode creative...")
    mc.execute_command("/gamemode creative")
    time.sleep(2)

    print("[AUTO] Giving sign...")
    mc.execute_command("/give @p oak_sign")
    time.sleep(2)

    print("[AUTO] Placing block...")
    mc.place_block()
    time.sleep(2)

    png = mc.screenshot()
    if png:
        save_screenshot(png, "auto_sign")


def interactive_loop(mc: McpClient):
    """Interactive command loop."""
    print("\n=== MCP Debug Interactive ===")
    print("Commands: ss | click X Y | btn N | key K | type T | chat T | cmd /c | widgets | release | place | use | wait N | quit")
    step = 0
    while True:
        try:
            line = input("> ").strip()
        except (EOFError, KeyboardInterrupt):
            break
        if not line:
            continue
        parts = line.split(None, 1)
        c = parts[0].lower()
        rest = parts[1] if len(parts) > 1 else ""

        if c == "quit":
            break
        elif c == "ss":
            name = rest or f"step{step}"
            png = mc.screenshot()
            if png:
                save_screenshot(png, name)
            else:
                print("  SS: FAILED")
        elif c == "click":
            xy = rest.split()
            if len(xy) >= 2:
                mc.click(int(xy[0]), int(xy[1]))
                print(f"  clicked ({xy[0]}, {xy[1]})")
            time.sleep(1)
        elif c == "btn":
            mc.click_button(int(rest))
            print(f"  button {rest}")
            time.sleep(1)
        elif c == "key":
            mc.press_key(rest)
            print(f"  key {rest}")
            time.sleep(1)
        elif c == "type":
            mc.type_text(rest)
            print(f"  typed {rest!r}")
            time.sleep(0.5)
        elif c == "chat":
            mc.execute_command(rest)
            print(f"  chat {rest!r}")
            time.sleep(1)
        elif c == "cmd":
            mc.execute_command(rest)
            print(f"  cmd {rest!r}")
            time.sleep(1)
        elif c == "widgets":
            w = mc.widgets()
            print(json.dumps(w, indent=2, ensure_ascii=False))
        elif c == "release":
            mc.release_mouse()
            print("  mouse released")
            time.sleep(0.5)
        elif c == "place":
            mc.place_block()
            print("  block placed")
            time.sleep(1)
        elif c == "use":
            mc.use_item()
            print("  item used")
            time.sleep(1)
        elif c == "wait":
            t = float(rest) if rest else 2
            print(f"  waiting {t}s...")
            time.sleep(t)
        elif c == "overlay":
            mc.cmd("overlay_show")
            time.sleep(0.2)
        else:
            print(f"  unknown: {c}")
        step += 1


def run_one_cmd(mc: McpClient, cmd_str: str):
    """Run a single command string like 'click 213 112'."""
    parts = cmd_str.split()
    if not parts:
        return
    c = parts[0]
    if c == "click" and len(parts) >= 3:
        mc.click(int(parts[1]), int(parts[2]))
    elif c == "btn" and len(parts) >= 2:
        mc.click_button(int(parts[1]))
    elif c == "key" and len(parts) >= 2:
        mc.press_key(parts[1])
    elif c == "type" and len(parts) >= 2:
        mc.type_text(" ".join(parts[1:]))
    elif c == "ss":
        png = mc.screenshot()
        if png:
            save_screenshot(png, parts[1] if len(parts) > 1 else "cmd")
    elif c == "release":
        mc.release_mouse()
    elif c == "place":
        mc.place_block()
    elif c == "use":
        mc.use_item()
    elif c == "wait" and len(parts) >= 2:
        time.sleep(float(parts[1]))
    time.sleep(1)


def main():
    parser = argparse.ArgumentParser(description="MC MCP debug tool")
    parser.add_argument("--no-build", action="store_true", help="Skip build step")
    parser.add_argument("--no-launch", action="store_true", help="Skip launch, connect to existing")
    parser.add_argument("--auto", action="store_true", help="Auto: enter world + screenshot")
    parser.add_argument("--auto-full", action="store_true", help="Full auto: build + launch + enter world + place sign")
    parser.add_argument("--create-world", action="store_true", help="Create Redstone Ready world from main menu")
    parser.add_argument("--cmd", help="Run single command and exit")
    args = parser.parse_args()

    mc_proc = None

    try:
        if not args.no_build:
            if not build_mod():
                return 1

        if not args.no_launch:
            kill_java()
            time.sleep(3)
            mc_proc = launch_mc()

        mc_client = McpClient()
        if args.no_launch:
            print("[CONNECT] Connecting to existing server...")
            if not mc_client.wait_ready(timeout=10):
                print("[CONNECT] No server found. Launch MC first.")
                return 1
        else:
            mc_client = wait_for_server(mc_proc)
            if not mc_client:
                return 1

        if args.cmd:
            run_one_cmd(mc_client, args.cmd)
        elif args.create_world:
            auto_create_redstone_world(mc_client)
        elif args.auto:
            auto_enter_world(mc_client)
            png = mc_client.screenshot()
            if png:
                save_screenshot(png, "auto_final")
        elif args.auto_full:
            auto_full_flow(mc_client)
        else:
            interactive_loop(mc_client)

    except KeyboardInterrupt:
        print("\nInterrupted")
    finally:
        if mc_proc and mc_proc.poll() is None:
            mc_proc.kill()
        print("[DONE]")


if __name__ == "__main__":
    sys.exit(main())
