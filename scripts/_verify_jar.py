import os, zipfile

jar = os.path.join(
    r"D:\source\projects\2026TeaCon\minecraft-neoforge-mcp",
    "packages", "mods", "1.21.7", "forge", "build", "libs",
    "minecraft-moddev-mcp-forge-1.21.7-1.0.0-SNAPSHOT.jar"
)
print(f"Jar exists: {os.path.exists(jar)}")
if os.path.exists(jar):
    z = zipfile.ZipFile(jar)
    name = "xyz/langyo/minecraft/mcp/common/McpHttpServer$CmdHandler.class"
    data = z.read(name)
    print(f"CmdHandler: {len(data)} bytes")
    print(f"  instanceof: {b'instanceof' in data}")
    print(f"  startsWith: {b'startsWith' in data}")
    print(f"  GSON.toJson: {b'GSON.toJson' in data}")
    z.close()
else:
    print("JAR NOT FOUND")
