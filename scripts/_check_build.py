import os, zipfile

base = r"D:\source\projects\2026TeaCon\minecraft-neoforge-mcp\packages\mods\1.21.7\forge"
cls_path = os.path.join(base, "build", "classes", "java", "main",
    "xyz", "langyo", "minecraft", "mcp", "common",
    "McpHttpServer$CmdHandler.class")
jar_path = os.path.join(base, "build", "libs",
    "minecraft-moddev-mcp-forge-1.21.7-1.0.0-SNAPSHOT.jar")

if os.path.exists(cls_path):
    data = open(cls_path, "rb").read()
    print(f"CmdHandler.class: {len(data)} bytes")
    print(f"  instanceof: {b'instanceof' in data}")
    print(f"  startsWith: {b'startsWith' in data}")
else:
    print(f"CmdHandler.class NOT FOUND at {cls_path}")
    # List what's there
    common_dir = os.path.join(base, "build", "classes", "java", "main",
        "xyz", "langyo", "minecraft", "mcp", "common")
    if os.path.isdir(common_dir):
        for f in sorted(os.listdir(common_dir)):
            print(f"  {f}: {os.path.getsize(os.path.join(common_dir, f))}b")
