import sys, time
sys.path.insert(0, r'D:\源代码\工程项目\2026TeaCon\minecraft-neoforge-mcp\scripts')
from mc_http import McpClient

c = McpClient()
WORLD_NAME = "RedstoneReady"

def wait_screen(expected=None, timeout=10):
    for _ in range(timeout * 2):
        r = c.cmd('get_screen_buttons')
        s = r.get('screen', 'unknown')
        if expected is None or s == expected:
            return s
        if s != 'unknown':
            return s
        time.sleep(0.5)
    return 'unknown'

def show_widgets():
    r = c.cmd('enumerate_widgets')
    for w in r.get('widgets', []):
        i=w['i']; cls=w['c']; x=w['x']; y=w['y']; ww=w['w']; h=w['h']
        print(f'  [{i}] {cls:20s} ({x},{y}) {ww}x{h}')
    return r.get('screen', 'unknown')

print("=== Step 1: TitleScreen -> SelectWorldScreen ===")
r = c.cmd('click_button_index', index=0)
print(f"Click singleplayer: {r}")
time.sleep(2)
s = wait_screen('SelectWorldScreen')
print(f"Screen: {s}")

print("\n=== Step 2: SelectWorldScreen -> CreateWorldScreen ===")
r = c.cmd('enumerate_widgets')
for w in r.get('widgets', []):
    i=w['i']; cls=w['c']; x=w['x']; y=w['y']; ww=w['w']; h=w['h']
    print(f'  [{i}] {cls:20s} ({x},{y}) {ww}x{h}')

r = c.cmd('click_button_index', index=2)
print(f"Click Create New World: {r}")
time.sleep(2)
s = wait_screen('CreateWorldScreen')
print(f"Screen: {s}")

print("\n=== Step 3: Set Creative mode ===")
r = c.cmd('enumerate_widgets')
for w in r.get('widgets', []):
    i=w['i']; cls=w['c']; x=w['x']; y=w['y']; ww=w['w']; h=w['h']
    print(f'  [{i}] {cls:20s} ({x},{y}) {ww}x{h}')

# Game mode cycle: survival(0) -> hardcore(1) -> creative(2)
for _ in range(2):
    r = c.cmd('click_button_index', index=5)
    print(f"  Cycle: newIdx={r.get('newIdx')}")
    time.sleep(0.3)

# Enable commands
r = c.cmd('click_button_index', index=7)
print(f"  Commands: newIdx={r.get('newIdx')}")

# Set world name
r = c.cmd('type_text', text=WORLD_NAME)
print(f"  World name typed")

print("\n=== Step 4: Switch to More tab ===")
r = c.cmd('switch_tab', tab_index=1)
print(f"Tab switch: {r}")
time.sleep(1)

print("\n=== Step 5: Set Superflat world type ===")
# World type CycleButton is at index 3
# Cycle to Superflat (newIdx=1 from our testing)
r = c.cmd('click_button_index', index=3)
print(f"World type cycle: newIdx={r.get('newIdx')}")
time.sleep(0.5)

# Verify: click Customize to check if it opens CreateFlatWorldScreen
r = c.cmd('click_button_index', index=4)
print(f"Customize click: {r}")
time.sleep(2)
s = wait_screen('CreateFlatWorldScreen')
print(f"Screen: {s}")

if s != 'CreateFlatWorldScreen':
    print("ERROR: Expected CreateFlatWorldScreen, got", s)
    print("Trying more cycles...")
    # Go back and try again
    c.cmd('press_key', key='escape')
    time.sleep(1)
    for cycle_pos in range(5):
        c.cmd('click_button_index', index=3)
        time.sleep(0.3)
        c.cmd('click_button_index', index=4)
        time.sleep(2)
        s = wait_screen('CreateFlatWorldScreen')
        if s == 'CreateFlatWorldScreen':
            print(f"  Found Superflat at cycle {cycle_pos}")
            break
    if s != 'CreateFlatWorldScreen':
        print("FATAL: Cannot find Superflat world type")
        sys.exit(1)

print("\n=== Step 6: CreateFlatWorldScreen -> Presets ===")
show_widgets()

# Presets button - need to find correct index
# Try index 4 first based on previous mapping
r = c.cmd('click_button_index', index=4)
print(f"Presets: {r}")
time.sleep(2)
s = wait_screen()
print(f"Screen: {s}")

if 'FlatPresets' not in s and 'Preset' not in s and s == 'CreateFlatWorldScreen':
    print("Index 4 didn't open presets, trying other indices...")
    for idx in range(6):
        r = c.cmd('click_button_index', index=idx)
        print(f"  Try [{idx}]: {r}")
        time.sleep(2)
        s = wait_screen()
        if 'FlatPresets' in s or 'Preset' in s or (s != 'CreateFlatWorldScreen' and s != 'unknown'):
            print(f"  Found presets screen: {s}")
            break

print(f"\nCurrent screen: {s}")
show_widgets()

print("\n=== Step 7: Select Redstone Ready preset ===")
# In MC 1.21.7, presets list: 0=Classic Flat, ..., 7=Redstone Ready, 8=The Void
# We need to select item 7 from the presets list
r = c.cmd('select_list_item', index=7)
print(f"Select preset 7: {r}")
time.sleep(1)

print("\n=== Step 8: Confirm preset selection ===")
show_widgets()
# Find and click Done/OK button
r = c.cmd('click_button_index', index=0)
print(f"Done: {r}")
time.sleep(2)
s = wait_screen()
print(f"Screen: {s}")

print("\n=== Step 9: Create the world ===")
# We should be back on CreateWorldScreen or CreateFlatWorldScreen
# Navigate back to CreateWorldScreen and click Create
for _ in range(3):
    if s == 'CreateWorldScreen':
        break
    c.cmd('press_key', key='escape')
    time.sleep(1)
    s = wait_screen()

show_widgets()

# Click "Create New World" button (usually index 1 or 2)
r = c.cmd('click_button_index', index=1)
print(f"Create: {r}")
time.sleep(3)
s = wait_screen()
print(f"Screen: {s}")

# If still on CreateWorldScreen, try the other button
if s == 'CreateWorldScreen':
    r = c.cmd('click_button_index', index=2)
    print(f"Create (alt): {r}")
    time.sleep(5)
    s = wait_screen()
    print(f"Screen: {s}")

print("\n=== Step 10: Wait for world load ===")
for i in range(30):
    time.sleep(2)
    r = c.cmd('get_player_info')
    if 'pos' in r:
        print(f"In-game! pos={r.get('pos')} dim={r.get('dimension')}")
        break
    print(f"  Waiting... ({i})")

print("\n=== Done! ===")
