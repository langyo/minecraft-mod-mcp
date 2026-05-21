import os

base = r"D:\source\projects\2026TeaCon\minecraft-neoforge-mcp\packages\mods\1.21.7\forge"
common_dir = os.path.join(base, "build", "classes", "java", "main",
    "xyz", "langyo", "minecraft", "mcp", "common")
print(f"Exists: {os.path.isdir(common_dir)}")
if os.path.isdir(common_dir):
    for f in sorted(os.listdir(common_dir)):
        fp = os.path.join(common_dir, f)
        print(f"  {f}: {os.path.getsize(fp)}b")
else:
    # Search for any .class files
    classes_dir = os.path.join(base, "build", "classes")
    if os.path.isdir(classes_dir):
        for root, dirs, files in os.walk(classes_dir):
            for f in files:
                if f.endswith(".class") and ("McpHttp" in f or "McpMessage" in f):
                    fp = os.path.join(root, f)
                    print(f"  FOUND: {fp} ({os.path.getsize(fp)}b)")
