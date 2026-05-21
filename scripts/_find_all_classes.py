import os

base = r"D:\source\projects\2026TeaCon\minecraft-neoforge-mcp\packages\mods\1.21.7\forge"
build_dir = os.path.join(base, "build")
print(f"Build dir exists: {os.path.isdir(build_dir)}")
for root, dirs, files in os.walk(build_dir):
    rel = os.path.relpath(root, base)
    for f in files:
        if f.endswith(".class"):
            fp = os.path.join(root, f)
            print(f"  {os.path.join(rel, f)} ({os.path.getsize(fp)}b)")
