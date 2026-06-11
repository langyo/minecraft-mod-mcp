import os, glob

base = r'D:\源代码\工程项目\2026TeaCon\minecraft-mcp\packages\mods'

new_block = '  "name": "ModDev MCP",\n  "description": "WebSocket bridge for AI agent interaction",'

fixed = 0
for f in glob.glob(os.path.join(base, '**/fabric/src/main/resources/fabric.mod.json'), recursive=True):
    with open(f, 'r', encoding='utf-8') as fh:
        content = fh.read()
    if '"en_us"' in content and '"ModDev MCP"' in content:
        lines = content.split('\n')
        new_lines = []
        i = 0
        while i < len(lines):
            line = lines[i].strip()
            if '"name": {' in line and 'en_us' in lines[i+1] if i+1 < len(lines) else '':
                new_lines.append(new_block)
                while i < len(lines) and '},' not in lines[i]:
                    i += 1
                i += 1
            elif '"description": {' in line and 'en_us' in lines[i+1] if i+1 < len(lines) else '':
                while i < len(lines) and '},' not in lines[i]:
                    i += 1
                i += 1
            else:
                new_lines.append(lines[i])
                i += 1
        content = '\n'.join(new_lines)
        with open(f, 'w', encoding='utf-8', newline='\n') as fh:
            fh.write(content)
        parts = f.split(os.sep)
        print(f'Fixed: {parts[-3]}/{parts[-2]}/{parts[-1]}')
        fixed += 1

print(f'\nTotal fixed: {fixed}')
