"""Simple debug server that serves the MCP debug HTML page.
Usage: python scripts/debug_page.py [port]
"""
import http.server
import json
import os
import sys
import threading
import urllib.request

MC_PORT = 9876
MC_HOST = "127.0.0.1"

DEBUG_HTML = r"""<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><title>MCP Debug</title>
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{background:#0d1117;color:#c9d1d9;font-family:'Segoe UI',system-ui,sans-serif;height:100vh;display:flex;flex-direction:column;overflow:hidden}
.hdr{background:#161b22;border-bottom:1px solid #30363d;padding:8px 16px;display:flex;align-items:center;gap:12px;flex-shrink:0}
.hdr h1{font-size:16px;color:#58a6ff}
.dot{width:10px;height:10px;border-radius:50%;background:#3fb950}
.main{display:flex;flex:1;overflow:hidden}
.left{flex:1;display:flex;flex-direction:column;border-right:1px solid #30363d;min-width:0}
.imgbox{flex:1;overflow:auto;background:#010409;display:flex;align-items:flex-start;justify-content:center;padding:8px}
.imgbox img{max-width:100%;height:auto;image-rendering:pixelated}
.vctrl{background:#161b22;padding:6px 12px;display:flex;gap:8px;align-items:center;border-top:1px solid #30363d;flex-shrink:0}
.vctrl button{background:#21262d;color:#c9d1d9;border:1px solid #30363d;padding:4px 12px;border-radius:4px;cursor:pointer;font-size:12px}
.vctrl button:hover{background:#30363d}
.vctrl button.active{background:#238636;border-color:#2ea043;color:#fff}
.info{color:#8b949e;font-size:11px;font-family:monospace}
.tabs{display:flex;gap:0}
.tabs button{border-radius:0;margin:0;background:#21262d;color:#8b949e;font-size:11px;padding:4px 10px}
.tabs button.active{background:#161b22;color:#58a6ff;border-bottom:2px solid #58a6ff}
.right{width:440px;display:flex;flex-direction:column;flex-shrink:0}
.rhdr{background:#161b22;padding:8px 12px;border-bottom:1px solid #30363d;display:flex;align-items:center;justify-content:space-between;flex-shrink:0}
.rhdr h2{font-size:14px;color:#58a6ff}
.rhdr span{font-size:11px;color:#8b949e}
.rhdr button{background:#21262d;color:#c9d1d9;border:1px solid #30363d;padding:2px 8px;border-radius:3px;cursor:pointer;font-size:10px}
.clist{flex:1;overflow-y:auto;padding:4px}
.cent{background:#161b22;border:1px solid #30363d;border-radius:4px;margin-bottom:4px;font-size:11px;overflow:hidden}
.cent .ch{padding:6px 8px;display:flex;align-items:center;gap:6px;cursor:pointer}
.cent .ch:hover{background:#1c2128}
.dir{font-size:9px;padding:1px 4px;border-radius:3px;font-weight:600;text-transform:uppercase}
.dir.res{background:#3fb95033;color:#3fb950}
.dir.err{background:#f8514933;color:#f85149}
.mtd{color:#d2a8ff;font-family:monospace;font-weight:600}
.dur{color:#8b949e;font-size:10px;margin-left:auto}
.ts{color:#484f58;font-size:10px;margin-left:6px}
.cb{display:none;padding:4px 8px 8px;border-top:1px solid #21262d}
.cb.open{display:block}
.cb pre{color:#8b949e;font-size:10px;white-space:pre-wrap;word-break:break-all;max-height:200px;overflow-y:auto;background:#0d1117;padding:6px;border-radius:3px;margin-top:4px}
.cpanel{background:#161b22;border-top:1px solid #30363d;padding:8px 12px;flex-shrink:0}
.crow{display:flex;gap:4px}
.crow input,.crow select{background:#0d1117;color:#c9d1d9;border:1px solid #30363d;padding:4px 6px;border-radius:3px;font-size:11px;font-family:monospace}
.crow input{flex:1}
.crow button{background:#21262d;color:#c9d1d9;border:1px solid #30363d;padding:4px 8px;border-radius:3px;cursor:pointer;font-size:11px}
.crow button:hover{background:#30362d}
.crow button.send{background:#1f6feb;border-color:#388bfd;color:#fff}
</style></head><body>
<div class="hdr"><h1>MCP Debug</h1><span class="dot"></span><span class="info" id="st">Connected</span></div>
<div class="main">
<div class="left">
<div class="imgbox" id="imgbox"><img id="img" src="" alt="screenshot"/></div>
<div class="vctrl">
<div class="tabs"><button id="tO" class="active" onclick="showTab('O')">Original</button><button id="tG" onclick="showTab('G')">Grid</button></div>
<button id="btnPoll" onclick="togglePoll()">Start Capture</button>
<span class="info" id="fps">--</span>
<span class="info" id="res"></span>
</div></div>
<div class="right">
<div class="rhdr"><h2>MCP Calls</h2><span id="cc">0 calls</span><button onclick="clearC()">Clear</button></div>
<div class="clist" id="cl"></div>
<div class="cpanel"><div class="crow">
<select id="sel" onchange="onSel()">
<option value="screenshot">screenshot</option><option value="click">click</option><option value="inject_click">inject_click</option>
<option value="press_key">press_key</option><option value="type_text">type_text</option><option value="scroll">scroll</option>
<option value="hotkey">hotkey</option><option value="execute_command">command</option><option value="get_player_info">player_info</option>
<option value="get_world_info">world_info</option><option value="get_screen_buttons">screen_buttons</option>
</select><input id="ex" placeholder='{"x":100,"y":200}'/><button class="send" onclick="sendC()">Send</button>
</div></div></div></div>
<script>
let polling=false,iv=null,fc=0,tot=0,showGrid=false;
let lastO='',lastG='';
function showTab(t){showGrid=t==='G';document.getElementById('tO').className=t==='O'?'active':'';document.getElementById('tG').className=t==='G'?'active':'';updateImg()}
function updateImg(){document.getElementById('img').src=showGrid&&lastG?lastG:lastO||''}
async function fetchSS(){try{const r=await fetch('/api/screenshot');if(!r.ok)return;const d=await r.json();if(d.original){lastO=d.original;lastG=d.grid||'';updateImg();fc++;document.getElementById('res').textContent=d.width+'x'+d.height}}catch(e){}}
function togglePoll(){polling=!polling;const b=document.getElementById('btnPoll');if(polling){b.textContent='Stop';b.className='active';iv=setInterval(fetchSS,200)}else{b.textContent='Start Capture';b.className='';clearInterval(iv);iv=null}}
setInterval(()=>{document.getElementById('fps').textContent=Math.round(fc*5)+' fps';fc=0},1000);
function connE(){const es=new EventSource('/api/events');es.onmessage=ev=>{try{addC(JSON.parse(ev.data))}catch(e){}};es.onerror=()=>setTimeout(connE,3000)}
function esc(s){const d=document.createElement('div');d.textContent=s;return d.innerHTML}
function addC(e){tot++;document.getElementById('cc').textContent=tot+' calls';const d=document.createElement('div');d.className='cent';
const cls=e.error?'err':'res';const dur=e.duration_ms>0?e.duration_ms+'ms':'';const ts=e.timestamp?new Date(e.timestamp).toISOString().split('T')[1].split('.')[0]:'';
const err=e.error?' <span style="color:#f85149">'+esc(e.error.substring(0,80))+'</span>':'';
const res=e.result?'<pre>'+esc(typeof e.result==='string'?e.result.substring(0,500):JSON.stringify(e.result).substring(0,500))+'</pre>':'';
d.innerHTML='<div class="ch" onclick="this.nextElementSibling.classList.toggle(\'open\')"><span class="dir '+cls+'">'+esc(e.direction)+'</span><span class="mtd">'+esc(e.method)+'</span>'+err+'<span class="dur">'+dur+'</span><span class="ts">'+ts+'</span></div><div class="cb">'+res+'</div>';
document.getElementById('cl').prepend(d);const cl=document.getElementById('cl');while(cl.children.length>200)cl.removeChild(cl.lastChild)}
function clearC(){document.getElementById('cl').innerHTML='';tot=0;document.getElementById('cc').textContent='0 calls'}
function onSel(){const v=document.getElementById('sel').value;const h={click:'{"x":100,"y":200}',inject_click:'{"x":100,"y":200}',press_key:'{"key":"enter"}',type_text:'{"text":"hello"}',scroll:'{"clicks":3}',hotkey:'{"keys":"ctrl+a"}',execute_command:'{"command":"/time set day"}'};document.getElementById('ex').placeholder=h[v]||''}
async function sendC(){const cmd=document.getElementById('sel').value;let ex={};try{ex=JSON.parse(document.getElementById('ex').value||'{}')}catch(e){}const body={cmd,...ex};try{const r=await fetch('/api/cmd',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});const d=await r.text();addC({timestamp:Date.now(),direction:'response',method:cmd,result:d,error:null,duration_ms:0})}catch(e){addC({timestamp:Date.now(),direction:'response',method:cmd,result:null,error:e.message,duration_ms:0})}}
connE();onSel();fetchSS();
</script></body></html>"""


class ProxyHandler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == '/' or self.path == '/index.html':
            self.send_response(200)
            self.send_header('Content-Type', 'text/html; charset=utf-8')
            self.end_headers()
            self.wfile.write(DEBUG_HTML.encode())
        elif self.path.startswith('/api/'):
            self._proxy('GET')
        else:
            self.send_error(404)

    def do_POST(self):
        if self.path.startswith('/api/'):
            self._proxy('POST')
        else:
            self.send_error(404)

    def _proxy(self, method):
        url = f'http://{MC_HOST}:{MC_PORT}{self.path}'
        try:
            if method == 'GET':
                r = urllib.request.urlopen(url, timeout=30)
            else:
                length = int(self.headers.get('Content-Length', 0))
                body = self.rfile.read(length) if length > 0 else b''
                req = urllib.request.Request(url, data=body, headers={'Content-Type': 'application/json'})
                r = urllib.request.urlopen(req, timeout=30)
            self.send_response(r.status)
            for h in ['Content-Type', 'Access-Control-Allow-Origin']:
                v = r.headers.get(h)
                if v:
                    self.send_header(h, v)
            self.end_headers()
            data = r.read()
            self.wfile.write(data)
        except Exception as e:
            self.send_response(502)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({"error": str(e)}).encode())

    def log_message(self, format, *args):
        pass


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
    server = http.server.HTTPServer(('0.0.0.0', port), ProxyHandler)
    print(f'MCP Debug page: http://127.0.0.1:{port}')
    print(f'Proxying API calls to {MC_HOST}:{MC_PORT}')
    server.serve_forever()


if __name__ == '__main__':
    main()
