"""Full Redstone Ready world creation - step by step with verification.
Starts from ANY state, gets to main menu first, then creates world.
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
    sz = __import__('os').path.getsize(path) if __import__('os').path.exists(path) else 0
    print(f'  [{step:02d}] {name} ({sz//1024}KB)')
    return path

def info():
    r = mc.cmd('enumerate_widgets')
    scr = r.get('screen', '?')
    ws = r.get('widgets', [])
    print(f'  Screen={scr} widgets={len(ws)}')
    for w in ws:
        if w.get('press'):
            print(f'    [{w["i"]}] {w["c"]:15s} ({w["x"]},{w["y"]})')

# ======== STEP 1: Get to main menu ========
print('\n[1] Pause game...')
mc.cmd('pause_game')
time.sleep(2)
snap('paused')

print('[1b] Save & Quit to title...')
r = mc.cmd('enumerate_widgets')
ws = r.get('widgets', [])
# Find "Save and Quit" button - usually last or second-to-last
for w in ws:
    if w.get('c') == 'Button' and w.get('y', 0) > 170:
        mc.click_button(w['i'])
        break
time.sleep(5)
snap('title')
info()

# ======== STEP 2: Singleplayer ========
print('\n[2] Singleplayer...')
mc.click_button(0)
time.sleep(3)
snap('selectworld')
info()

# ======== STEP 3: Create New World (index 2 on SelectWorldScreen) ========
print('\n[3] Create New World...')
mc.click_button(2)
time.sleep(3)
snap('createworld')
info()

# Verify we're on CreateWorldScreen
r = mc.cmd('enumerate_widgets')
if 'CreateWorldScreen' not in r.get('screen', ''):
    print('  ERROR: Not on CreateWorldScreen! Got:', r.get('screen'))
    # Try anyway

# ======== STEP 4: Switch to World tab (param=index) ========
print('\n[4] Switch to World tab...')
r = mc.cmd('switch_tab', index=1)
print(f'  Result: {r}')
time.sleep(1)
snap('world_tab')
info()

# ======== STEP 5: Cycle world type to Superflat ========
print('\n[5] Cycling world type to Superflat...')
# Current type is Default. Need to cycle: Default -> Superflat (1 click)
# But let's cycle once and check
mc.click_button(3)
time.sleep(1)
snap('after_cycle')
info()

# ======== STEP 6: Click Customize (index 4) ========
print('\n[6] Click Customize...')
mc.click_button(4)
time.sleep(2)
snap('customize_opened')
r = mc.cmd('enumerate_widgets')
scr = r.get('screen', '')
print(f'  Opened: {scr}')
info()

if 'Flat' not in scr and 'Customize' not in scr:
    print('  WARNING: Not flat customize screen. Trying more cycles...')
    mc.press_key('Escape')
    time.sleep(1)
    # Try cycling more
    for extra in range(4):
        mc.click_button(3)
        time.sleep(0.8)
        mc.click_button(4)
        time.sleep(2)
        r2 = mc.cmd('enumerate_widgets')
        s2 = r2.get('screen', '')
        snap(f'try_{extra}')
        if 'Flat' in s2:
            print(f'  SUCCESS after {extra+1} extra cycles!')
            info()
            break
        mc.press_key('Escape')
        time.sleep(0.5)

# ======== STEP 7: Presets ========
print('\n[7] Opening Presets...')
r = mc.cmd('enumerate_widgets')
ws = r.get('widgets', [])
# Find Presets button - usually one of the buttons
preset_btn_idx = None
for w in ws:
    if w.get('c') == 'Button' and w.get('press'):
        preset_btn_idx = w['i']
        break

if preset_btn_idx is not None:
    mc.click_button(preset_btn_idx)
else:
    # Fallback: try index 3
    mc.click_button(3)
time.sleep(2)
snap('presets_open')
info()

# ======== STEP 8: Select Redstone Ready preset using inject_click ========
print('\n[8] Selecting Redstone Ready preset via inject_click...')
# From debug page grid analysis: red presets are around x=320-400, y=400
# First red icon (Redstone Ready) at approximately x=335, y=402
# Use inject_click for platform-level mouse injection
r = mc.cmd('inject_click', x=335, y=402)
print(f'  inject_click result: {r}')
time.sleep(1)
snap('after_preset_click')

# Also try clicking the "Use Preset" / "使用预设" button
r = mc.cmd('enumerate_widgets')
ws = r.get('widgets', [])
use_btn = None
for w in ws:
    if w.get('c') == 'Button' and w.get('press'):
        use_btn = w['i']
        break
if use_btn is not None:
    print(f'\n[9] Clicking Use Preset (btn {use_btn})...')
    mc.click_button(use_btn)
    time.sleep(1)
    snap('after_use_preset')

# ======== STEP 10: Done ========
print('\n[10] Clicking Done...')
mc.click_button(0)
time.sleep(1)
snap('done_customize')

# ======== STEP 11: Back to Game tab, set Creative ========
print('\n[11] Switch to Game tab...')
mc.cmd('switch_tab', index=0)
time.sleep(1)

print('[12] Setting Creative (cycle 2x)...')
mc.click_button(5)
time.sleep(0.5)
mc.click_button(5)
time.sleep(0.5)
snap('ready_create')

# ======== STEP 13: Create! ========
print('\n[13] Creating world...')
mc.click_button(1)
time.sleep(30)

print('\n[14] Releasing mouse + screenshot...')
mc.release_mouse()
time.sleep(2)
snap('in_world')

print('\n=== DONE ===')
