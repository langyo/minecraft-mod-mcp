"""
MCP Debug Stream Viewer

Serves a web page at http://localhost:8888 that displays the MC video stream.
The page connects to the Rust MCP server's /stream WebSocket endpoint.

Usage:
    python scripts/debug_stream.py [--mcp-host 127.0.0.1] [--mcp-port 9876] [--port 8888]
"""

import argparse
import http.server
import socketserver
import threading
import webbrowser
import sys
import os

HTML_PAGE = r"""
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>MCP Stream Viewer</title>
<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body { background: #1a1a2e; color: #eee; font-family: 'Consolas', 'Monaco', monospace; }
#toolbar {
    background: #16213e; padding: 10px 16px; display: flex; align-items: center; gap: 12px;
    border-bottom: 1px solid #0f3460;
}
#toolbar button {
    background: #0f3460; color: #e94560; border: 1px solid #e94560; padding: 6px 14px;
    cursor: pointer; font-family: inherit; font-size: 13px; border-radius: 3px;
}
#toolbar button:hover { background: #e94560; color: #fff; }
#toolbar button.active { background: #e94560; color: #fff; }
#status { font-size: 12px; color: #888; }
#fps { font-size: 12px; color: #53d769; margin-left: auto; }
#container {
    display: flex; justify-content: center; align-items: center;
    min-height: calc(100vh - 44px); position: relative;
}
canvas {
    max-width: 100%; max-height: calc(100vh - 44px);
    image-rendering: pixelated; border: 1px solid #333;
}
#overlay {
    position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%);
    color: #555; font-size: 18px; text-align: center;
}
#overlay .sub { font-size: 12px; margin-top: 8px; color: #444; }
</style>
</head>
<body>
<div id="toolbar">
    <button id="btnStart" onclick="startVideo()">Start Stream</button>
    <button id="btnStop" onclick="stopVideo()" disabled>Stop Stream</button>
    <span id="status">Disconnected</span>
    <span id="fps"></span>
</div>
<div id="container">
    <canvas id="video"></canvas>
    <div id="overlay">
        <div>No Stream</div>
        <div class="sub">Click "Start Stream" to begin</div>
    </div>
</div>
<script>
const MCP_WS = `ws://${location.hostname}:__MCP_PORT__/stream`;
const MCP_CTRL = `ws://${location.hostname}:__MCP_PORT__/`;
const canvas = document.getElementById('video');
const ctx = canvas.getContext('2d');
const statusEl = document.getElementById('status');
const fpsEl = document.getElementById('fps');
const overlay = document.getElementById('overlay');
const btnStart = document.getElementById('btnStart');
const btnStop = document.getElementById('btnStop');

let streamWs = null;
let ctrlWs = null;
let frameCount = 0;
let lastFpsTime = performance.now();

function sendCtrl(method) {
    if (ctrlWs && ctrlWs.readyState === WebSocket.OPEN) {
        ctrlWs.send(JSON.stringify({jsonrpc:"2.0",method:method,params:{},id:Date.now()}));
    } else {
        ctrlWs = new WebSocket(MCP_CTRL);
        ctrlWs.onopen = () => {
            ctrlWs.send(JSON.stringify({jsonrpc:"2.0",method:method,params:{},id:Date.now()}));
        };
    }
}

function startVideo() {
    overlay.style.display = 'none';
    btnStart.disabled = true;
    btnStop.disabled = false;
    btnStart.classList.remove('active');
    btnStop.classList.add('active');

    sendCtrl('video_start');

    if (streamWs) streamWs.close();
    streamWs = new WebSocket(MCP_WS);
    streamWs.binaryType = 'arraybuffer';

    streamWs.onopen = () => {
        statusEl.textContent = 'Stream connected';
        statusEl.style.color = '#53d769';
    };

    streamWs.onmessage = (e) => {
        const blob = new Blob([e.data], {type: 'image/jpeg'});
        const url = URL.createObjectURL(blob);
        const img = new Image();
        img.onload = () => {
            if (canvas.width !== img.width || canvas.height !== img.height) {
                canvas.width = img.width;
                canvas.height = img.height;
            }
            ctx.drawImage(img, 0, 0);
            URL.revokeObjectURL(url);
            frameCount++;
        };
        img.src = url;
    };

    streamWs.onclose = () => {
        statusEl.textContent = 'Stream disconnected';
        statusEl.style.color = '#e94560';
    };

    streamWs.onerror = () => {
        statusEl.textContent = 'Stream error';
        statusEl.style.color = '#e94560';
    };

    setInterval(() => {
        const now = performance.now();
        const elapsed = (now - lastFpsTime) / 1000;
        const fps = Math.round(frameCount / elapsed);
        fpsEl.textContent = fps + ' fps';
        frameCount = 0;
        lastFpsTime = now;
    }, 1000);
}

function stopVideo() {
    sendCtrl('video_stop');
    if (streamWs) { streamWs.close(); streamWs = null; }
    btnStart.disabled = false;
    btnStop.disabled = true;
    btnStop.classList.remove('active');
    statusEl.textContent = 'Stopped';
    statusEl.style.color = '#888';
    fpsEl.textContent = '';
    overlay.style.display = '';
}
</script>
</body>
</html>
"""


def main():
    parser = argparse.ArgumentParser(description="MCP Debug Stream Viewer")
    parser.add_argument("--mcp-host", default="127.0.0.1", help="MCP server host")
    parser.add_argument("--mcp-port", type=int, default=9876, help="MCP server WS port")
    parser.add_argument("--port", type=int, default=8888, help="Debug viewer port")
    args = parser.parse_args()

    html = HTML_PAGE.replace("__MCP_PORT__", str(args.mcp_port))

    class Handler(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            self.wfile.write(html.encode("utf-8"))

        def log_message(self, format, *args):
            pass

    with socketserver.ThreadingTCPServer(("", args.port), Handler) as httpd:
        url = f"http://{args.mcp_host}:{args.port}"
        print(f"MCP Debug Stream Viewer")
        print(f"  Viewer:    {url}")
        print(f"  MC Stream: ws://{args.mcp_host}:{args.mcp_port}/stream")
        print(f"  Ctrl+C to stop")
        try:
            webbrowser.open(url)
        except Exception:
            pass
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\nStopped.")
            httpd.shutdown()


if __name__ == "__main__":
    main()
