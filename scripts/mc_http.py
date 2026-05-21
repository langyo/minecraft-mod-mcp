"""MCP HTTP client library for interacting with embedded MC mod server.
All scripts can: from mc_http import McpClient

Usage:
    mc = McpClient()           # default http://127.0.0.1:9876
    mc.wait_ready(timeout=120) # wait for server to be up
    ss = mc.screenshot()       # returns bytes (PNG)
    mc.cmd("click_button_index", index=0)
    mc.cmd("click", x=213, y=112)
    mc.cmd("open_chat")
    mc.cmd("type_text", text="/gamemode creative")
    mc.cmd("press_key", key="Escape")
    mc.cmd("release_mouse")
    mc.cmd("place_block")
    mc.cmd("use_item")
    mc.cmd("overlay_hide")
    mc.cmd("overlay_show")
    mc.status()                # returns dict
    mc.widgets()               # enumerate widgets from screenshot
"""

import json
import time
import base64
import os
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import URLError


class McpClient:
    def __init__(self, host="127.0.0.1", port=9876):
        self.base = f"http://{host}:{port}"

    def _post(self, path, body=None, timeout=30):
        data = json.dumps(body).encode() if body else None
        req = Request(f"{self.base}{path}", data=data,
                      headers={"Content-Type": "application/json"} if data else {})
        resp = urlopen(req, timeout=timeout)
        raw = resp.read().decode()
        try:
            return json.loads(raw)
        except json.JSONDecodeError:
            return {"raw": raw}

    def _get(self, path, timeout=30):
        req = Request(f"{self.base}{path}")
        resp = urlopen(req, timeout=timeout)
        return resp.read()

    def is_ready(self):
        try:
            self._get("/api/status", timeout=3)
            return True
        except Exception:
            return False

    def wait_ready(self, timeout=120, interval=3):
        deadline = time.time() + timeout
        while time.time() < deadline:
            if self.is_ready():
                return True
            time.sleep(interval)
        return False

    def status(self):
        return self._get("/api/status")

    def cmd(self, method, **kwargs):
        body = {"cmd": method, **kwargs}
        try:
            return self._post("/api/cmd", body)
        except Exception as e:
            return {"error": str(e)}

    def screenshot(self, overlay=False, save_path=None):
        """Take screenshot. Returns bytes (PNG) or None."""
        if not overlay:
            self.cmd("overlay_hide")
            time.sleep(0.2)
        try:
            raw = self._get("/api/screenshot", timeout=30)
            data = json.loads(raw.decode())
            b64 = data.get("original", "")
            if b64.startswith("data:image/png;base64,"):
                b64 = b64[len("data:image/png;base64,"):]
            png = base64.b64decode(b64)
            if save_path:
                Path(save_path).parent.mkdir(parents=True, exist_ok=True)
                with open(save_path, "wb") as f:
                    f.write(png)
            return png
        except Exception as e:
            return None

    def widgets(self):
        """Get enumerated widgets from status."""
        try:
            raw = self._get("/api/status", timeout=10)
            return json.loads(raw.decode())
        except:
            return {}

    # High-level convenience methods
    def click(self, x, y):
        return self.cmd("click", x=x, y=y)

    def drag(self, x1, y1, x2, y2, button="left"):
        return self.cmd("drag", x1=x1, y1=y1, x2=x2, y2=y2, button=button)

    def click_button(self, index):
        return self.cmd("click_button_index", index=index)

    def press_key(self, key):
        return self.cmd("press_key", key=key)

    def type_text(self, text):
        return self.cmd("type_text", text=text)

    def open_chat(self):
        return self.cmd("open_chat")

    def close_screen(self):
        return self.cmd("close_screen")

    def release_mouse(self):
        return self.cmd("release_mouse")

    def place_block(self):
        return self.cmd("place_block")

    def use_item(self):
        return self.cmd("use_item")

    def execute_command(self, command):
        """Send a chat command like /gamemode creative"""
        self.open_chat()
        time.sleep(0.3)
        self.type_text(command)
        time.sleep(0.2)
        self.press_key("Enter")
        time.sleep(0.5)
