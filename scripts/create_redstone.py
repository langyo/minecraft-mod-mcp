"""Create Redstone Ready world step by step with screenshot verification.

Assumes MC is at the title screen / main menu.
"""
import sys, time, json
sys.path.insert(0, r'D:\源代码\工程项目\2026TeaCon\minecraft-neoforge-mcp\scripts')
from mc_http import McpClient

SS = r'D:\源代码\工程项目\2026TeaCon\minecraft-neoforge-mcp\screenshots\world_create'
mc = McpClient()
step = 0

def snap(name):
    global step
    step += 1
    path = f'{SS}/{step:02d}_{name}.png'
    mc.screenshot(save_path=path)
    print(f'  [{step}] {name}')
    return path

def widgets():
    r = mc.cmd('enumerate_widgets')
    ws = r.get('widgets', [])
    for w in ws:
        pressable = w.get('press', False)
        cls = w.get('c', '?')
        idx = w.get('i', -1)
        xy = f"({w.get('x',0)},{w.get('y',0)})"
        label = f" w={w.get('w',0)} h={w.get('h',0)}"
        marker = " <--" if pressable and cls in ('Button', 'CycleButton') else ""
        print(f"    [{idx}] {cls:15s} {xy:12s}{label}{marker}")

# ========== START ==========
print("=== Step: Title screen ===")
snap('title')
widgets()

# Click Singleplayer
print("\n=== Click Singleplayer ===")
mc.click_button(0)
time.sleep(3)
snap('selectworld')
widgets()

# Click Create New World (index 2 on SelectWorldScreen)
print("\n=== Click Create New World (index 2) ===")
mc.click_button(2)
time.sleep(3)
snap('createworld')
widgets()

# Switch to World tab (param name is 'index', not 'tab')
print("\n=== Switch to World tab ===")
r = mc.cmd('switch_tab', index=1)
print(f'  switch_tab result: {r}')
time.sleep(1)
snap('world_tab')
widgets()

# Click World Type cycle button (index 3) until superflat
# We need to cycle until we get superflat. Let's try clicking once and check.
print("\n=== Cycle world type ===")
mc.click_button(3)
time.sleep(1)
snap('cycle_1')
widgets()

# Click Customize (index 4) - only works for superflat
print("\n=== Click Customize (index 4) ===")
mc.click_button(4)
time.sleep(2)
snap('customize')
widgets()
screen = mc.cmd('enumerate_widgets').get('screen', '')
print(f'  Current screen: {screen}')

if 'Flat' in screen or 'Superflat' in screen or 'Customize' in screen:
    print("\n=== Customize screen opened! ===")
    # Look for Presets button
    r = mc.cmd('enumerate_widgets')
    for w in r.get('widgets', []):
        if w.get('press') and w.get('c') == 'Button':
            print(f"  Button [{w['i']}] at ({w['x']},{w['y']})")

    # Click Presets (usually first button)
    mc.click_button(0)
    time.sleep(2)
    snap('presets')
    widgets()

    # Select Redstone Ready - need to scroll up and click
    mc.cmd('scroll', clicks=-5)
    time.sleep(1)
    mc.click(200, 120)
    time.sleep(1)
    snap('selected_preset')

    # Click Use Preset
    mc.click_button(1)
    time.sleep(1)
    snap('use_preset')

    # Click Done
    mc.click_button(0)
    time.sleep(1)
    snap('back_to_world_tab')
else:
    print(f"\n  ERROR: Customize opened wrong screen: {screen}")
    print("  World type is probably not superflat. Need more cycles.")
    mc.press_key('Escape')
    time.sleep(1)

    # Try more cycles
    for extra in range(6):
        mc.click_button(3)
        time.sleep(0.8)
        mc.click_button(4)
        time.sleep(2)
        r2 = mc.cmd('enumerate_widgets')
        s2 = r2.get('screen', '')
        snap(f'try_cycle_{extra}')
        if 'Flat' in s2 or 'Customize' in s2:
            print(f'  Found flat/customize screen after {extra+1} extra cycles!')
            break
        mc.press_key('Escape')
        time.sleep(0.5)

print("\nDone!")
