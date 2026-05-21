import os, zipfile

base = r"D:\源代码\工程项目\2026TeaCon\minecraft-neoforge-mcp\packages\mods\1.21.7\forge"
cls = os.path.join(base, "build", "classes", "java", "main", "xyz", "langyo",
                     "minecraft", "mcp", "common", "McpHttpServer$CmdHandler.class")
jar_path = os.path.join(base, "build", "libs", "minecraft-moddev-mcp-forge-1.21.7-1.0.0-SNAPSHOT.jar")

c = open(cls, "rb").read() if os.path.exists(cls) else b"MISSING"
z = zipfile.ZipFile(jar_path)
j = z.read("xyz/langyo/minecraft/mcp/common/McpHttpServer$CmdHandler.class")
z.close()

print(f"build/classes: {len(c)} bytes, instanceof={b'instanceof' in c}")
print(f"jar:           {len(j)} bytes, instanceof={b'instanceof' in j}")
print(f"sizes match:   {len(c) == len(j)}")
