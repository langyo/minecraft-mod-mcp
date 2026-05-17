import socket, json, time, sys, os, shutil, glob, base64

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from test_version import clear_mods
from version_config import ALL_VERSIONS

MC_KEY = sys.argv[1] if len(sys.argv) > 1 else "1.16.5"
CMD = sys.argv[2] if len(sys.argv) > 2 else "screenshot"

mc_info = ALL_VERSIONS.get(MC_KEY, {})
VERSION = mc_info.get("version_id", MC_KEY)

jars = glob.glob(os.path.join(os.path.dirname(__file__), "..", "mods", MC_KEY, "forge", "build", "libs", "*.jar"))
mod_jar = [j for j in jars if "sources" not in j][0]
clear_mods()
mcp_mods = os.path.expandvars(r"%USERPROFILE%\.mcp\mods")
os.makedirs(mcp_mods, exist_ok=True)
shutil.copy2(mod_jar, mcp_mods)

def send_cmd(cmd_dict, timeout=120):
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(timeout)
    sock.connect(("127.0.0.1", 9877))
    sock.sendall((json.dumps(cmd_dict) + "\n").encode())
    buf = b""
    while True:
        data = sock.recv(65536)
        if not data:
            break
        buf += data
        if b"\n" in buf:
            line, _ = buf.split(b"\n", 1)
            result = json.loads(line)
            sock.close()
            return result
    sock.close()
    return {"error": "no response"}

r = send_cmd({"cmd": "launch", "version": VERSION}, timeout=180)
print(f"Launch: {r.get('status')} ws={r.get('ws')}")
if not r.get("ws"):
    sys.exit(1)

time.sleep(25)

if CMD == "command":
    r = send_cmd({"cmd": "command", "text": "time set day"}, timeout=10)
    print(f"Command: {json.dumps(r)[:200]}")
elif CMD == "screenshot":
    r = send_cmd({"cmd": "screenshot", "name": "test"}, timeout=60)
    if r.get("status") == "ok" and r.get("data", "").startswith("data:image"):
        img_data = base64.b64decode(r["data"].split(",")[1])
        out_path = os.path.join(os.environ["TEMP"], f"test_{MC_KEY}.png")
        with open(out_path, "wb") as f:
            f.write(img_data)
        print(f"Screenshot OK: {len(img_data)} bytes -> {out_path}")
    else:
        print(f"Screenshot: {json.dumps(r)[:500]}")

send_cmd({"cmd": "kill"}, timeout=15)
