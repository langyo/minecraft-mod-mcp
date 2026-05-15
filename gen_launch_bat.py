import json, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "scripts"))
from launch_mc import merge_version_json, build_classpath, extract_natives, build_jvm_args, build_game_args, find_java

mc = os.environ["APPDATA"] + r"\.minecraft"
vj = merge_version_json("1.21.7-forge-57.0.2", mc)
cp = build_classpath(vj, mc)
natives = extract_natives(vj, mc)
jvm = build_jvm_args(vj, natives, mc)
game = build_game_args(vj, "1.21.7-forge-57.0.2", mc)

sep = ";"
cmd_parts = [
    find_java(21),
    "-Xmx4G", "-Xms1G",
    "-Dmcp.server=ws://127.0.0.1:9876",
] + jvm + [
    "-cp", sep.join(cp),
    vj["mainClass"],
] + game

bat_path = os.path.join(os.path.dirname(__file__), "launch_test.bat")
with open(bat_path, "w") as f:
    f.write("@echo off\n")
    f.write("set MC_MCP_SERVER=ws://127.0.0.1:9876\n")
    for i, p in enumerate(cmd_parts):
        if " " in p:
            f.write('"' + p + '" ')
        else:
            f.write(p + " ")
    f.write("\n")
print(f"Wrote {bat_path} with {len(cmd_parts)} args")
