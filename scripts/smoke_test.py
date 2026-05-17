"""Smoke test: launch MC, navigate menus, create a Redstone Ready superflat world.

Demonstrates end-to-end MCP control: screenshot -> vision analysis -> click -> type -> verify.

Usage:
  python scripts/smoke_test.py 1.21.7-forge-57.0.2
  python scripts/smoke_test.py 26.1.2-forge-64.0.8
"""

import argparse
import base64
import json
import os
import subprocess
import sys
import time
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
    import importlib
    try:
        mcp = importlib.import_module("zai-mcp-server")
        analyze_fn = getattr(mcp, "analyze_image", None)
        if analyze_fn:
            result = analyze_fn(image_source=ss_path, prompt=prompt)
            return result
    except Exception:
        pass
    print(f"  [VISION] Image saved at {ss_path} (manual analysis needed)")
    return None


def find_button_positions(ss_path, button_labels):
    if not ss_path:
        return {}
    try:
        from zai_mcp_server import analyze_image
    except ImportError:
        print("  [VISION] zai-mcp-server not available, using hardcoded positions")
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


def main():
    parser = argparse.ArgumentParser(description="Smoke test: create Redstone Ready world")
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

    is_chinese = True
    print(f"============================================================")
    print(f"SMOKE TEST: {version}/{loader}")
    print(f"  Language: {'Chinese' if is_chinese else 'English'}")
    print(f"============================================================")

    kill_all_java()
    time.sleep(3)

    server_proc = None
    mc_proc = None
    try:
        # Step 0: Build mod
        print("[0/8] Building mod...")
        mod_jar = find_mod_jar(version, loader)
        if not mod_jar:
            print(f"  ERROR: Mod JAR not found for {version}/{loader}")
            return 1
        print(f"  Mod: {mod_jar.name} ({mod_jar.stat().st_size // 1024}KB)")

        # Step 1: Start MCP server
        print("[1/8] Starting MCP server...")
        server_proc = _start_mcp_server()
        time.sleep(3)

        # Step 2: Install mod + launch MC
        print("[2/8] Installing mod and launching MC...")
        clear_mods()
        install_mod(version, loader)
        mc_proc = _start_mc(version, loader)
        if not mc_proc:
            print("  ERROR: Failed to launch MC")
            return 1

        # Step 3: Wait for mod connection
        print("[3/8] Waiting for mod connection...")
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
                    if "MOD CONNECTED" in content or "MC connected" in content:
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

        # Step 4: Screenshot main menu
        print("[4/8] Screenshot main menu...")
        ss = take_screenshot_via_server(server_proc, "01_main_menu")
        if ss:
            print(f"  Screenshot: {ss} ({os.path.getsize(ss) // 1024}KB)")
        else:
            print("  WARNING: No screenshot")

        # Analyze the screenshot to find button positions
        single_player_label = "单人游戏"
        if is_chinese else "Single Player"
        create_world_label = "创建新的世界" if is_chinese else "Create New World"
        game_mode_label = "游戏模式" if is_chinese else "Game Mode"
        more_options_label = "更多世界的选项..." if is_chinese else "More World Options..."
        done_label = "完成" if is_chinese else "Done"
        cancel_label = "取消" if is_chinese else "Cancel"

        print("[5/8] Analyzing main menu...")
        buttons = find_button_positions(ss, [single_player_label])
        sp_pos = buttons.get(single_player_label)

        if sp_pos:
            print(f"  {single_player_label} found at ({sp_pos['x']}, {sp_pos['y']})")
            click_x, click_y = sp_pos["x"], sp_pos["y"]
        else:
            print(f"  Button not detected, using default center position")
            click_x, click_y = 512, 300

        # Step 5: Click Single Player
        print(f"[5/8] Clicking '{single_player_label}' at ({click_x}, {click_y})...")
        send_and_wait(server_proc, "click", {"x": click_x, "y": click_y})
        time.sleep(3)

        # Step 6: Screenshot world selection
        print("[6/8] Screenshot world selection screen...")
        ss2 = take_screenshot_via_server(server_proc, "02_world_select")
        if ss2:
            print(f"  Screenshot: {ss2} ({os.path.getsize(ss2) // 1024}KB)")

        # Find "Create New World" button
        buttons2 = find_button_positions(ss2, [create_world_label])
        cnw_pos = buttons2.get(create_world_label)

        if cnw_pos:
            print(f"  {create_world_label} found at ({cnw_pos['x']}, {cnw_pos['y']})")
            cnw_x, cnw_y = cnw_pos["x"], cnw_pos["y"]
        else:
            cnw_x, cnw_y = 512, 350

        print(f"  Clicking '{create_world_label}' at ({cnw_x}, {cnw_y})...")
        send_and_wait(server_proc, "click", {"x": cnw_x, "y": cnw_y})
        time.sleep(3)

        # Step 7: Screenshot world creation screen
        print("[7/8] Screenshot world creation screen...")
        ss3 = take_screenshot_via_server(server_proc, "03_create_world")
        if ss3:
            print(f"  Screenshot: {ss3} ({os.path.getsize(ss3) // 1024}KB)")

        # Click "More World Options" button (bottom-left area in MC)
        buttons3 = find_button_positions(ss3, [more_options_label, game_mode_label])
        mo_pos = buttons3.get(more_options_label)

        if mo_pos:
            mo_x, mo_y = mo_pos["x"], mo_pos["y"]
        else:
            mo_x, mo_y = 350, 420

        print(f"  Clicking '{more_options_label}' at ({mo_x}, {mo_y})...")
        send_and_wait(server_proc, "click", {"x": mo_x, "y": mo_y})
        time.sleep(2)

        # Screenshot more options
        ss4 = take_screenshot_via_server(server_proc, "04_more_options")
        if ss4:
            print(f"  Screenshot: {ss4} ({os.path.getsize(ss4) // 1024}KB)")

        # Click World Type button to cycle to Superflat
        # In MC, the world type button is near the top of the More Options tab
        # We need to click it multiple times to cycle through types to "Superflat/超平坦"
        superflat_label = "超平坦" if is_chinese else "Superflat"
        wt_buttons = find_button_positions(ss4, ["World Type", "世界类型", superflat_label])
        wt_pos = wt_buttons.get("World Type") or wt_buttons.get("世界类型")
        if wt_pos:
            wt_x, wt_y = wt_pos["x"], wt_pos["y"]
        else:
            wt_x, wt_y = 350, 100

        print(f"  Cycling world type at ({wt_x}, {wt_y}) to '{superflat_label}'...")
        for i in range(8):
            send_and_wait(server_proc, "click", {"x": wt_x, "y": wt_y}, wait=0.5)
            time.sleep(0.5)
            ss_cycle = take_screenshot_via_server(server_proc, f"05_cycle_{i}")
            if ss_cycle:
                print(f"  Cycle {i}: {os.path.getsize(ss_cycle) // 1024}KB")
            cycle_btns = find_button_positions(ss_cycle, [superflat_label])
            if cycle_btns.get(superflat_label):
                print(f"  Found '{superflat_label}'!")
                break

        # Now click "Customize" button (next to the world type)
        customize_label = "自定义" if is_chinese else "Customize"
        time.sleep(1)
        ss5 = take_screenshot_via_server(server_proc, "06_pre_customize")
        cust_buttons = find_button_positions(ss5, [customize_label])
        cust_pos = cust_buttons.get(customize_label)
        if cust_pos:
            cust_x, cust_y = cust_pos["x"], cust_pos["y"]
        else:
            cust_x, cust_y = 680, 100

        print(f"  Clicking '{customize_label}' at ({cust_x}, {cust_y})...")
        send_and_wait(server_proc, "click", {"x": cust_x, "y": cust_y})
        time.sleep(2)

        # Screenshot customization screen
        ss6 = take_screenshot_via_server(server_proc, "07_customize")
        if ss6:
            print(f"  Screenshot: {ss6} ({os.path.getsize(ss6) // 1024}KB)")

        # Click "Presets" button
        presets_label = "预设" if is_chinese else "Presets"
        preset_buttons = find_button_positions(ss6, [presets_label])
        preset_pos = preset_buttons.get(presets_label)
        if preset_pos:
            preset_x, preset_y = preset_pos["x"], preset_pos["y"]
        else:
            preset_x, preset_y = 260, 350

        print(f"  Clicking '{presets_label}' at ({preset_x}, {preset_y})...")
        send_and_wait(server_proc, "click", {"x": preset_x, "y": preset_y})
        time.sleep(2)

        # Screenshot presets screen
        ss7 = take_screenshot_via_server(server_proc, "08_presets")
        if ss7:
            print(f"  Screenshot: {ss7} ({os.path.getsize(ss7) // 1024}KB)")

        # Find and click "Redstone Ready" preset
        redstone_label = "红石就绪" if is_chinese else "Redstone Ready"
        rs_buttons = find_button_positions(ss7, [redstone_label])
        rs_pos = rs_buttons.get(redstone_label)
        if rs_pos:
            rs_x, rs_y = rs_pos["x"], rs_pos["y"]
            print(f"  Found '{redstone_label}' at ({rs_x}, {rs_y})")
        else:
            print(f"  '{redstone_label}' not detected, trying scroll + click")
            rs_x, rs_y = 200, 150
            send_and_wait(server_proc, "scroll", {"clicks": -3}, wait=0.5)
            time.sleep(1)

        print(f"  Clicking '{redstone_label}' at ({rs_x}, {rs_y})...")
        send_and_wait(server_proc, "click", {"x": rs_x, "y": rs_y})
        time.sleep(1)

        # Click "Use Preset" / "使用预设" or "Done" / "完成"
        use_label = "使用预设" if is_chinese else "Use Preset"
        ss8 = take_screenshot_via_server(server_proc, "09_selected_preset")
        use_buttons = find_button_positions(ss8, [use_label, done_label])
        use_pos = use_buttons.get(use_label) or use_buttons.get(done_label)
        if use_pos:
            use_x, use_y = use_pos["x"], use_pos["y"]
        else:
            use_x, use_y = 512, 350

        print(f"  Clicking '{use_label}' at ({use_x}, {use_y})...")
        send_and_wait(server_proc, "click", {"x": use_x, "y": use_y})
        time.sleep(1)

        # Click Done on customize screen
        ss9 = take_screenshot_via_server(server_proc, "10_customize_done")
        done_buttons = find_button_positions(ss9, [done_label])
        done_pos = done_buttons.get(done_label)
        if done_pos:
            d_x, d_y = done_pos["x"], done_pos["y"]
        else:
            d_x, d_y = 512, 350

        print(f"  Clicking '{done_label}' at ({d_x}, {d_y})...")
        send_and_wait(server_proc, "click", {"x": d_x, "y": d_y})
        time.sleep(1)

        # Step 8: Click "Create New World" / "创建新的世界" to start
        print("[8/8] Creating world...")
        ss10 = take_screenshot_via_server(server_proc, "11_ready_create")
        final_create = "创建新的世界" if is_chinese else "Create New World"
        fc_buttons = find_button_positions(ss10, [final_create])
        fc_pos = fc_buttons.get(final_create)
        if fc_pos:
            fc_x, fc_y = fc_pos["x"], fc_pos["y"]
        else:
            fc_x, fc_y = 512, 420

        print(f"  Clicking '{final_create}' at ({fc_x}, {fc_y})...")
        send_and_wait(server_proc, "click", {"x": fc_x, "y": fc_y})

        # Wait for world to load
        print("  Waiting for world to load...")
        for i in range(60):
            time.sleep(5)
            ss_ingame = take_screenshot_via_server(server_proc, f"12_ingame_{i}")
            if ss_ingame:
                size_kb = os.path.getsize(ss_ingame) // 1024
                print(f"  [{i*5}s] Screenshot: {size_kb}KB")

                ingame_prompt = (
                    "Is this showing a Minecraft in-game world (not a menu)? "
                    "Look for: hotbar at bottom, crosshair in center, terrain/blocks visible. "
                    "Answer ONLY 'YES' or 'NO'."
                )
                result = analyze_screenshot(ss_ingame, ingame_prompt)
                if result and "YES" in str(result).upper():
                    print(f"\n  ✓ WORLD LOADED! In-game after {i*5}s")

                    final_ss = take_screenshot_via_server(server_proc, "13_final")
                    if final_ss:
                        print(f"  Final screenshot: {final_ss} ({os.path.getsize(final_ss) // 1024}KB)")
                    print(f"\n  SMOKE TEST: PASS")
                    return 0

        print(f"\n  SMOKE TEST: FAIL (world did not load in 5 minutes)")
        return 1

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
