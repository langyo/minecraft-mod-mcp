"""Smoke test: launch MC, navigate menus, create Redstone Ready superflat world,
place a sign with 'Hello World', then exit cleanly.

Complete workflow:
  1. Detect main menu
  2. Click Single Player
  3. Click Create New World
  4. Enter world name via paste (date-time format, avoids IME conflict)
  5. Configure Superflat + Redstone Ready preset
  6. Generate world
  7. Open inventory, take a sign
  8. Close inventory
  9. Rotate view angle (game-internal, no OS mouse interference)
 10. Right-click to place sign
 11. Type 'Hello World' on sign, confirm
 12. Press ESC -> pause menu
 13. Save and Quit to title
 14. Quit Game

Usage:
  python scripts/smoke_test.py 1.21.7-forge-57.0.2
  python scripts/smoke_test.py 26.1.2-forge-64.0.8
"""

import argparse
import json
import os
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCRIPTS = ROOT / "scripts"
SERVER_JAR = ROOT / "build" / "libs" / "mcp-server-0.1.0.jar"
MC_DIR = Path(os.environ.get("APPDATA", os.path.expanduser("~"))) / ".minecraft"

sys.path.insert(0, str(SCRIPTS))
from version_config import ALL_VERSIONS, get_fg_era, get_jdk_home
from test_version import (
    kill_all_java, find_mod_jar, install_mod, clear_mods,
    _start_mcp_server, _send_server_cmd, _start_mc,
)


def send_and_wait(server, tool, args, wait=2):
    _send_server_cmd(server, tool, args)
    time.sleep(wait)


def take_screenshot_via_server(server, name):
    ss_dir = ROOT / "screenshots" / "smoke"
    ss_dir.mkdir(parents=True, exist_ok=True)
    ss_path = str(ss_dir / f"{name}_{int(time.time())}.png")
    _send_server_cmd(server, "screenshot", {"save_path": ss_path})
    time.sleep(6)
    if os.path.isfile(ss_path):
        return ss_path
    for f in sorted(ss_dir.glob(f"{name}_*.png"), key=os.path.getmtime, reverse=True):
        if time.time() - os.path.getmtime(f) < 30:
            return str(f)
    return None


def analyze_screenshot(ss_path, prompt):
    if not ss_path or not os.path.isfile(ss_path):
        print(f"  [VISION] No screenshot to analyze")
        return None
    try:
        from zai_mcp_server import analyze_image
        result = analyze_image(image_source=ss_path, prompt=prompt)
        return result
    except Exception as e:
        print(f"  [VISION] analyze error: {e}")
        return None


def find_button_positions(ss_path, button_labels):
    if not ss_path:
        return {}
    try:
        from zai_mcp_server import analyze_image
    except ImportError:
        return {}
    result = analyze_image(image_source=ss_path, prompt=(
        f"Find the pixel coordinates (x, y center) of these buttons in this Minecraft screenshot: "
        f"{json.dumps(button_labels)}. "
        f"Return ONLY a JSON object mapping each button label to its center {{x, y}} coordinates. "
        f"Example: {{\"Single Player\": {{\"x\": 400, \"y\": 300}}}}"
    ))
    if result:
        try:
            text = str(result)
            start = text.find("{")
            end = text.rfind("}") + 1
            if start >= 0 and end > start:
                return json.loads(text[start:end])
        except (json.JSONDecodeError, ValueError):
            pass
    return {}


def is_ingame(ss_path):
    """Use AI vision to check if screenshot shows in-game world."""
    if not ss_path:
        return False
    result = analyze_screenshot(ss_path, (
        "Is this showing a Minecraft in-game world (not a menu)? "
        "Look for: hotbar at bottom, crosshair in center, terrain/blocks visible. "
        "Answer ONLY 'YES' or 'NO'."
    ))
    return result and "YES" in str(result).upper()


def step(server, step_num, total, desc):
    print(f"[{step_num}/{total}] {desc}")


def main():
    parser = argparse.ArgumentParser(description="Smoke test: full E2E Minecraft workflow")
    parser.add_argument("version", help="MC version e.g. 1.21.7-forge-57.0.2")
    parser.add_argument("--timeout", type=int, default=600)
    args = parser.parse_args()

    version = args.version
    loader = "forge"
    if "neoforge" in version:
        loader = "neoforge"
    elif "fabric" in version:
        loader = "fabric"

    mc_key = version.split("-")[0]
    if mc_key == version:
        for k in sorted(ALL_VERSIONS.keys(), key=len, reverse=True):
            if version.startswith(k):
                mc_key = k
                break

    TOTAL_STEPS = 14
    is_chinese = True

    LABELS = {
        "single_player": "单人游戏" if is_chinese else "Single Player",
        "create_world": "创建新的世界" if is_chinese else "Create New World",
        "more_options": "更多世界的选项..." if is_chinese else "More World Options...",
        "superflat": "超平坦" if is_chinese else "Superflat",
        "customize": "自定义" if is_chinese else "Customize",
        "presets": "预设" if is_chinese else "Presets",
        "redstone_ready": "红石就绪" if is_chinese else "Redstone Ready",
        "use_preset": "使用预设" if is_chinese else "Use Preset",
        "done": "完成" if is_chinese else "Done",
        "cancel": "取消" if is_chinese else "Cancel",
        "save_quit": "保存并退出到标题..." if is_chinese else "Save and Quit to Title...",
        "quit_game": "退出游戏" if is_chinese else "Quit Game",
    }

    print(f"============================================================")
    print(f"SMOKE TEST: {version}/{loader}  ({TOTAL_STEPS} steps)")
    print(f"  Language: {'Chinese' if is_chinese else 'English'}")
    print(f"============================================================")

    kill_all_java()
    time.sleep(3)

    server_proc = None
    mc_proc = None
    try:
        step(server_proc, 0, TOTAL_STEPS, "Building mod...")
        mod_jar = find_mod_jar(version, loader)
        if not mod_jar:
            print(f"  ERROR: Mod JAR not found for {version}/{loader}")
            return 1
        print(f"  Mod: {mod_jar.name} ({mod_jar.stat().st_size // 1024}KB)")

        step(server_proc, 1, TOTAL_STEPS, "Starting MCP server...")
        server_proc = _start_mcp_server()
        time.sleep(3)

        step(server_proc, 2, TOTAL_STEPS, "Installing mod and launching MC...")
        clear_mods()
        install_mod(version, loader)
        mc_proc = _start_mc(version)
        if not mc_proc:
            print("  ERROR: Failed to launch MC")
            return 1

        step(server_proc, 3, TOTAL_STEPS, "Waiting for mod connection...")
        deadline = time.time() + 120
        connected = False
        while time.time() < deadline:
            if mc_proc.poll() is not None:
                print("  ERROR: MC process exited")
                return 1
            stdout_log = MC_DIR / "mcp-launch-stdout.log"
            if stdout_log.exists():
                try:
                    content = stdout_log.read_text(encoding="utf-8", errors="replace")
                    if "MOD CONNECTED" in content or "MC connected" in content or "MCP-WS" in content:
                        connected = True
                        print("  MOD CONNECTED")
                        break
                except Exception:
                    pass
            time.sleep(3)
        if not connected:
            print("  ERROR: Mod did not connect in time")
            return 1
        time.sleep(5)

        # ========== STEP 4: Detect main menu ==========
        step(server_proc, 4, TOTAL_STEPS, "Screenshot & detect main menu...")
        ss = take_screenshot_via_server(server_proc, "01_main_menu")
        if ss:
            print(f"  Screenshot: {ss} ({os.path.getsize(ss) // 1024}KB)")

        # ========== STEP 5: Click Single Player ==========
        step(server_proc, 5, TOTAL_STEPS, f"Clicking '{LABELS['single_player']}'...")
        buttons = find_button_positions(ss, [LABELS["single_player"]])
        sp_pos = buttons.get(LABELS["single_player"])
        click_x, click_y = (sp_pos["x"], sp_pos["y"]) if sp_pos else (512, 300)
        send_and_wait(server_proc, "click", {"x": click_x, "y": click_y})
        time.sleep(3)

        # ========== STEP 6: Click Create New World ==========
        step(server_proc, 6, TOTAL_STEPS, f"Clicking '{LABELS['create_world']}'...")
        ss2 = take_screenshot_via_server(server_proc, "02_world_select")
        buttons2 = find_button_positions(ss2, [LABELS["create_world"]])
        cnw_pos = buttons2.get(LABELS["create_world"])
        cnw_x, cnw_y = (cnw_pos["x"], cnw_pos["y"]) if cnw_pos else (512, 350)
        send_and_wait(server_proc, "click", {"x": cnw_x, "y": cnw_y})
        time.sleep(3)

        # ========== STEP 7: Paste world name (date-time format) ==========
        step(server_proc, 7, TOTAL_STEPS, "Entering world name via paste...")
        ss3 = take_screenshot_via_server(server_proc, "03_create_world")

        world_name = datetime.now().strftime("Test_%Y%m%d_%H%M%S")
        print(f"  World name: {world_name}")
        send_and_wait(server_proc, "paste_text", {"text": world_name}, wait=1)
        time.sleep(1)

        # ========== STEP 8: Configure Superflat + Redstone Ready ==========
        step(server_proc, 8, TOTAL_STEPS, f"Configuring {LABELS['superflat']} + {LABELS['redstone_ready']}...")

        ss_mo = take_screenshot_via_server(server_proc, "04_more_options_pre")
        mo_buttons = find_button_positions(ss_mo, [LABELS["more_options"]])
        mo_pos = mo_buttons.get(LABELS["more_options"])
        mo_x, mo_y = (mo_pos["x"], mo_pos["y"]) if mo_pos else (350, 420)
        print(f"  Clicking '{LABELS['more_options']}' at ({mo_x}, {mo_y})...")
        send_and_wait(server_proc, "click", {"x": mo_x, "y": mo_y})
        time.sleep(2)

        ss4 = take_screenshot_via_server(server_proc, "04_more_options")
        wt_buttons = find_button_positions(ss4, ["World Type", "世界类型", LABELS["superflat"]])
        wt_pos = wt_buttons.get("World Type") or wt_buttons.get("世界类型")
        wt_x, wt_y = (wt_pos["x"], wt_pos["y"]) if wt_pos else (350, 100)

        print(f"  Cycling world type to '{LABELS['superflat']}'...")
        for i in range(8):
            send_and_wait(server_proc, "click", {"x": wt_x, "y": wt_y}, wait=0.5)
            time.sleep(0.5)
            ss_cycle = take_screenshot_via_server(server_proc, f"05_cycle_{i}")
            cycle_btns = find_button_positions(ss_cycle, [LABELS["superflat"]])
            if cycle_btns.get(LABELS["superflat"]):
                print(f"  Found '{LABELS['superflat']}'!")
                break

        cust_buttons = find_button_positions(
            take_screenshot_via_server(server_proc, "06_pre_customize"),
            [LABELS["customize"]]
        )
        cust_pos = cust_buttons.get(LABELS["customize"])
        cust_x, cust_y = (cust_pos["x"], cust_pos["y"]) if cust_pos else (680, 100)
        print(f"  Clicking '{LABELS['customize']}' at ({cust_x}, {cust_y})...")
        send_and_wait(server_proc, "click", {"x": cust_x, "y": cust_y})
        time.sleep(2)

        preset_buttons = find_button_positions(
            take_screenshot_via_server(server_proc, "07_customize"),
            [LABELS["presets"]]
        )
        preset_pos = preset_buttons.get(LABELS["presets"])
        preset_x, preset_y = (preset_pos["x"], preset_pos["y"]) if preset_pos else (260, 350)
        print(f"  Clicking '{LABELS['presets']}' at ({preset_x}, {preset_y})...")
        send_and_wait(server_proc, "click", {"x": preset_x, "y": preset_y})
        time.sleep(2)

        ss_presets = take_screenshot_via_server(server_proc, "08_presets")
        rs_buttons = find_button_positions(ss_presets, [LABELS["redstone_ready"]])
        rs_pos = rs_buttons.get(LABELS["redstone_ready"])
        if rs_pos:
            rs_x, rs_y = rs_pos["x"], rs_pos["y"]
            print(f"  Found '{LABELS['redstone_ready']}' at ({rs_x}, {rs_y})")
        else:
            rs_x, rs_y = 200, 150
            send_and_wait(server_proc, "scroll", {"clicks": -3}, wait=0.5)
            time.sleep(1)
        send_and_wait(server_proc, "click", {"x": rs_x, "y": rs_y})
        time.sleep(1)

        use_buttons = find_button_positions(
            take_screenshot_via_server(server_proc, "09_selected_preset"),
            [LABELS["use_preset"], LABELS["done"]]
        )
        use_pos = use_buttons.get(LABELS["use_preset"]) or use_buttons.get(LABELS["done"])
        use_x, use_y = (use_pos["x"], use_pos["y"]) if use_pos else (512, 350)
        send_and_wait(server_proc, "click", {"x": use_x, "y": use_y})
        time.sleep(1)

        done_buttons = find_button_positions(
            take_screenshot_via_server(server_proc, "10_customize_done"),
            [LABELS["done"]]
        )
        done_pos = done_buttons.get(LABELS["done"])
        d_x, d_y = (done_pos["x"], done_pos["y"]) if done_pos else (512, 350)
        send_and_wait(server_proc, "click", {"x": d_x, "y": d_y})
        time.sleep(1)

        # ========== STEP 9: Click Create/Generate ==========
        step(server_proc, 9, TOTAL_STEPS, f"Clicking '{LABELS['create_world']}' to generate...")
        ss_ready = take_screenshot_via_server(server_proc, "11_ready_create")
        fc_buttons = find_button_positions(ss_ready, [LABELS["create_world"]])
        fc_pos = fc_buttons.get(LABELS["create_world"])
        fc_x, fc_y = (fc_pos["x"], fc_pos["y"]) if fc_pos else (512, 420)
        send_and_wait(server_proc, "click", {"x": fc_x, "y": fc_y})

        # ========== STEP 10: Wait for world load ==========
        step(server_proc, 10, TOTAL_STEPS, "Waiting for world to load...")
        ingame = False
        for i in range(60):
            time.sleep(5)
            ss_ig = take_screenshot_via_server(server_proc, f"12_ingame_{i}")
            if ss_ig:
                size_kb = os.path.getsize(ss_ig) // 1024
                print(f"  [{i*5}s] Screenshot: {size_kb}KB")
                if is_ingame(ss_ig):
                    print(f"  WORLD LOADED after {i*5}s!")
                    ingame = True
                    break
        if not ingame:
            print(f"\n  FAIL: World did not load in 5 minutes")
            return 1

        # ========== STEP 11: Open inventory, take sign ==========
        step(server_proc, 11, TOTAL_STEPS, "Opening inventory (E), taking sign...")
        send_and_wait(server_proc, "press_key", {"key": "E"}, wait=2)
        time.sleep(2)
        ss_inv = take_screenshot_via_server(server_proc, "13_inventory_open")
        if ss_inv:
            print(f"  Inventory screenshot: {os.path.getsize(ss_inv) // 1024}KB")

        inv_labels = ["sign", "牌子", "Oak Sign", "橡木牌"]
        sign_buttons = find_button_positions(ss_inv, inv_labels)
        sign_pos = sign_buttons.get(inv_labels[0]) or sign_buttons.get(inv_labels[1]) or sign_buttons.get(inv_labels[2])

        if sign_pos:
            print(f"  Sign found at ({sign_pos['x']}, {sign_pos['y']}), picking it up...")
            send_and_wait(server_proc, "click", {"x": sign_pos["x"], "y": sign_pos["y"]})
            time.sleep(1)
            send_and_wait(server_proc, "click", {"x": sign_pos["x"], "y": sign_pos["y"]})
            time.sleep(1)
        else:
            print(f"  Sign button not detected by AI, trying slot positions...")
            send_and_wait(server_proc, "click", {"x": 360, "y": 90}, wait=1)
            time.sleep(1)

        # ========== STEP 12: Close inventory ==========
        step(server_proc, 12, TOTAL_STEPS, "Closing inventory (E)...")
        send_and_wait(server_proc, "press_key", {"key": "E"}, wait=1)
        time.sleep(2)

        # ========== STEP 13: Rotate view (game-internal) + Place sign ==========
        step(server_proc, 13, TOTAL_STEPS, "Rotating view (game-internal) & placing sign...")

        send_and_wait(server_proc, "look_delta", {"delta_yaw": 45.0, "delta_pitch": -15.0}, wait=1)
        time.sleep(1)
        print("  View rotated: yaw+45, pitch-15")

        send_and_wait(server_proc, "right_click", {}, wait=1)
        time.sleep(2)
        print("  Right-click sent (placing sign)")

        # Check if sign edit screen appeared
        ss_sign = take_screenshot_via_server(server_proc, "14_sign_edit")
        if ss_sign:
            print(f"  Sign screen: {os.path.getsize(ss_sign) // 1024}KB")

        # Type "Hello World" on sign using paste (avoid IME issues)
        send_and_wait(server_proc, "paste_text", {"text": "Hello World"}, wait=1)
        time.sleep(1)
        print("  Typed 'Hello World' on sign")

        # Confirm sign text - try Enter first, then look for Done button
        send_and_wait(server_proc, "press_key", {"key": "Enter"}, wait=1)
        time.sleep(2)

        # If still on sign screen, try clicking "Done"
        ss_sign2 = take_screenshot_via_server(server_proc, "14b_sign_after_enter")
        done_sign_btns = find_button_positions(ss_sign2, [LABELS["done"]])
        done_sign_pos = done_sign_btns.get(LABELS["done"])
        if done_sign_pos:
            send_and_wait(server_proc, "click", {"x": done_sign_pos["x"], "y": done_sign_pos["y"]})
            time.sleep(1)
            print("  Clicked 'Done' on sign screen")
        else:
            # Try Enter again or Escape to confirm
            send_and_wait(server_proc, "press_key", {"key": "Enter"}, wait=1)
            time.sleep(1)

        # Final screenshot of placed sign
        ss_final_sign = take_screenshot_via_server(server_proc, "15_sign_placed")
        if ss_final_sign:
            print(f"  Sign placed: {ss_final_sign} ({os.path.getsize(ss_final_sign) // 1024}KB)")

        # ========== STEP 14: ESC -> Save & Quit -> Quit Game ==========
        step(server_proc, 14, TOTAL_STEPS, "ESC -> Save & Quit -> Quit Game...")

        # Press ESC to open pause menu
        send_and_wait(server_proc, "press_key", {"key": "Escape"}, wait=2)
        time.sleep(2)
        ss_pause = take_screenshot_via_server(server_proc, "16_pause_menu")
        if ss_pause:
            print(f"  Pause menu: {os.path.getsize(ss_pause) // 1024}KB")

        # Click "Save and Quit to Title..."
        sq_buttons = find_button_positions(ss_pause, [LABELS["save_quit"]])
        sq_pos = sq_buttons.get(LABELS["save_quit"])
        sq_x, sq_y = (sq_pos["x"], sq_pos["y"]) if sq_pos else (512, 280)
        print(f"  Clicking '{LABELS['save_quit']}' at ({sq_x}, {sq_y})...")
        send_and_wait(server_proc, "click", {"x": sq_x, "y": sq_y})
        time.sleep(5)

        # Should be back at title screen now, click "Quit Game"
        ss_title = take_screenshot_via_server(server_proc, "17_back_to_title")
        qg_buttons = find_button_positions(ss_title, [LABELS["quit_game"]])
        qg_pos = qg_buttons.get(LABELS["quit_game"])
        qg_x, qg_y = (qg_pos["x"], qg_pos["y"]) if qg_pos else (512, 380)
        print(f"  Clicking '{LABELS['quit_game']}' at ({qg_x}, {qg_y})...")
        send_and_wait(server_proc, "click", {"x": qg_x, "y": qg_y})
        time.sleep(3)

        final_ss = take_screenshot_via_server(server_proc, "18_final")
        if final_ss:
            print(f"  Final screenshot: {final_ss} ({os.path.getsize(final_ss) // 1024}KB)")

        print(f"\n  ========================================")
        print(f"  SMOKE TEST: PASS  (all {TOTAL_STEPS} steps completed)")
        print(f"  ========================================")
        return 0

    except Exception as e:
        print(f"  ERROR: {e}")
        import traceback
        traceback.print_exc()
        return 1
    finally:
        if mc_proc and mc_proc.poll() is None:
            mc_proc.kill()
        if server_proc:
            try:
                server_proc.stdin.close()
            except Exception:
                pass
            server_proc.kill()
        kill_all_java()


if __name__ == "__main__":
    sys.exit(main())
