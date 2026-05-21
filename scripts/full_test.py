import sys, time, base64
sys.path.insert(0, r'D:\源代码\工程项目\2026TeaCon\minecraft-neoforge-mcp\scripts')
from mc_http import McpClient

c = McpClient()
WORLD_NAME = 'RSReady3'

def check():
    r = c.cmd('get_screen_buttons')
    return r.get('screen', 'unknown')

def widgets():
    r = c.cmd('enumerate_widgets')
    for w in r.get('widgets', []):
        print(f'  [{w["i"]}] {w["c"]:20s} ({w["x"]},{w["y"]}) {w["w"]}x{w["h"]}')

print('=== Step 1: Title -> SelectWorld -> CreateWorld ===')
c.cmd('click_button_index', index=0)
time.sleep(2)
print('Screen:', check())

c.cmd('click_button_index', index=2)
time.sleep(2)
print('Screen:', check())

print('\n=== Step 2: Set creative + world name ===')
for _ in range(2):
    c.cmd('click_button_index', index=5)
    time.sleep(0.3)
c.cmd('click_button_index', index=7)
time.sleep(0.3)
c.cmd('click', x=150, y=55)
time.sleep(0.2)
c.cmd('hotkey', keys='ctrl+a')
time.sleep(0.2)
c.cmd('type_text', text=WORLD_NAME)
time.sleep(0.3)

print('\n=== Step 3: More tab -> Superflat -> Customize ===')
c.cmd('switch_tab', tab_index=1)
time.sleep(1)
c.cmd('click_button_index', index=3)
time.sleep(0.5)
c.cmd('click_button_index', index=4)
time.sleep(2)
print('Screen:', check())

print('\n=== Step 4: Presets -> Redstone Ready ===')
c.cmd('click_button_index', index=3)
time.sleep(2)
print('Screen:', check())

c.cmd('select_list_item', index=7)
time.sleep(0.5)
c.cmd('click_button_index', index=2)
time.sleep(2)
print('Screen:', check())

print('\n=== Step 5: Done on CreateFlatWorldScreen (index 5) ===')
c.cmd('click_button_index', index=5)
time.sleep(2)
print('Screen:', check())

print('\n=== Step 6: Create world (index 1 on CreateWorldScreen) ===')
c.cmd('switch_tab', tab_index=0)
time.sleep(0.5)
c.cmd('click_button_index', index=1)
print('Creating...')
time.sleep(8)

for i in range(30):
    r = c.cmd('get_player_info')
    pos = r.get('pos', '')
    if pos:
        print(f'SUCCESS! pos={pos} dim={r.get("dimension")}')
        break
    time.sleep(2)
    print(f'Loading... {i}')
else:
    print('FAILED. Screen:', check())
    sys.exit(1)

print('\n=== Step 7: Set creative mode ===')
r = c.cmd('set_gamemode', mode='creative')
print('Gamemode:', r)
time.sleep(1)

print('\n=== Step 8: Test sendCommand fix ===')
r = c.cmd('execute_command', command='time set day')
print('Command:', r)
time.sleep(1)

print('\n=== Step 9: Place sign via commands ===')
Q = chr(34)

c.cmd('open_chat')
time.sleep(0.5)
c.cmd('type_text', text='/setblock ~2 ~ ~ minecraft:oak_sign')
time.sleep(0.3)
c.cmd('press_key', key='enter')
time.sleep(1)

c.cmd('open_chat')
time.sleep(0.5)
cmd = '/data modify block ~2 ~ ~ front_text.messages set value [' + Q + 'hello' + Q + ',' + Q + 'world' + Q + ',' + Q + Q + ',' + Q + Q + ']'
c.cmd('type_text', text=cmd)
time.sleep(0.3)
c.cmd('press_key', key='enter')
time.sleep(1)

c.cmd('open_chat')
time.sleep(0.5)
c.cmd('type_text', text='/data modify block ~2 ~ ~ is_waxed set value true')
time.sleep(0.3)
c.cmd('press_key', key='enter')
time.sleep(1)

c.cmd('press_key', key='escape')
time.sleep(0.3)

print('\n=== Step 10: Look at sign and screenshot ===')
c.cmd('set_view_angle', yaw=270, pitch=0)
time.sleep(1)

r = c.cmd('screenshot')
if r and 'data:image' in str(r):
    img_data = str(r).split(',', 1)[1]
    outpath = r'C:\Users\langy\AppData\Local\Temp\opencode\final_result.png'
    with open(outpath, 'wb') as f:
        f.write(base64.b64decode(img_data))
    print(f'Screenshot saved to {outpath}')
else:
    print('Screenshot failed')

print('\nDone!')
