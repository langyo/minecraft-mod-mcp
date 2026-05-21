"""Step-by-step world creation with screenshot at each step.
Each step takes a screenshot and saves it for analysis.

Usage:
  python scripts/create_world_debug.py
"""

import sys, time, json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCRIPTS = ROOT / "scripts"
SS_DIR = ROOT / "screenshots" / "world_debug"
SS_DIR.mkdir(parents=True, exist_ok=True)

sys.path.insert(0, str(SCRIPTS))
from mc_http import McpClient


def ss(mc, name):
    png = mc.screenshot()
    if png:
        path = SS_DIR / f"{name}.png"
        path.write_bytes(png)
        print(f"  [{name}] {len(png)//1024}KB -> {path}")
        return path
    print(f"  [{name}] FAILED")
    return None


def widgets(mc):
    """Get current widget list from /api/status."""
    import urllib.request
    try:
        resp = urllib.request.urlopen("http://127.0.0.1:9876/api/status", timeout=5)
        data = json.loads(resp.read())
        widgets_list = data.get("widgets", data.get("gui", []))
        if widgets_list:
            print(f"  Widgets ({len(widgets_list)}):")
            for i, w in enumerate(widgets_list):
                if isinstance(w, dict):
                    print(f"    [{i}] {w}")
                else:
                    print(f"    [{i}] {w}")
        return widgets_list
    except Exception as e:
        print(f"  Widget query failed: {e}")
        return []


def main():
    mc = McpClient()
    if not mc.wait_ready(timeout=5):
        print("Server not ready!")
        return

    print("=== Step 0: Main Menu ===")
    ss(mc, "00_main_menu")
    widgets(mc)

    print("\n=== Step 1: Click Singleplayer (btn 0) ===")
    mc.click_button(0)
    time.sleep(3)
    ss(mc, "01_world_select")
    widgets(mc)

    print("\n=== Step 2: Click Create New World (btn 0) ===")
    mc.click_button(0)
    time.sleep(3)
    ss(mc, "02_create_world_default")
    widgets(mc)

    print("\n=== Step 3: List all buttons before tab switch ===")
    # Try to enumerate widgets
    result = mc.cmd("enumerate_widgets")
    print(f"  enumerate_widgets result: {json.dumps(result, ensure_ascii=False)[:500]}")

    print("\n=== Step 4: Switch to World tab ===")
    result = mc.cmd("switch_tab", tab=1)
    print(f"  switch_tab result: {json.dumps(result, ensure_ascii=False)}")
    time.sleep(1)
    ss(mc, "03_world_tab")
    widgets(mc)

    print("\n=== Step 5: List buttons on World tab ===")
    result = mc.cmd("enumerate_widgets")
    print(f"  enumerate_widgets: {json.dumps(result, ensure_ascii=False)[:500]}")

    print("\n=== Step 6: Click WorldType cycle button ===")
    # Try button index 3 first
    mc.click_button(3)
    time.sleep(0.5)
    ss(mc, "04_after_cycle_1")
    widgets(mc)

    print("\n=== Step 7: Check if superflat ===")
    result = mc.cmd("enumerate_widgets")
    print(f"  widgets after cycle: {json.dumps(result, ensure_ascii=False)[:500]}")

    print("\n=== Step 8: Press Escape to cancel ===")
    mc.press_key("Escape")
    time.sleep(1)
    ss(mc, "05_cancelled")

    print("\nDone! Check screenshots in", SS_DIR)


if __name__ == "__main__":
    main()
