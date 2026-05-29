"""Simple WebSocket client for manual testing of the MCP server.

Usage:
  python scripts/ws_client.py                    # interactive mode
  python scripts/ws_client.py --action ping      # send single command
  python scripts/ws_client.py --action screenshot --save smoke.png
"""

import argparse
import asyncio
import base64
import json
import sys
import os

WS_PORT = 9876


async def ws_interactive():
    import websockets

    uri = f"ws://127.0.0.1:{WS_PORT}"
    print(f"Connecting to {uri} ...")
    try:
        async with websockets.connect(uri) as ws:
            print("Connected! Type JSON commands (or 'quit' to exit):")
            async def recv_loop():
                try:
                    async for msg in ws:
                        data = json.dumps(json.loads(msg), indent=2, ensure_ascii=False)
                        print(f"\n< {data[:1000]}")
                        print("> ", end="", flush=True)
                except websockets.ConnectionClosed:
                    print("\nConnection closed.")
            recv_task = asyncio.create_task(recv_loop())
            loop = asyncio.get_event_loop()
            while True:
                line = await loop.run_in_executor(None, lambda: input("> "))
                if line.strip().lower() in ("quit", "exit", "q"):
                    break
                if not line.strip():
                    continue
                try:
                    obj = json.loads(line)
                except json.JSONDecodeError:
                    obj = {"action": line.strip(), "params": {"requestId": "manual-1"}}
                await ws.send(json.dumps(obj))
            recv_task.cancel()
    except Exception as e:
        print(f"Error: {e}")


async def ws_send_action(action, params=None, save_screenshot=None):
    import websockets

    uri = f"ws://127.0.0.1:{WS_PORT}"
    try:
        async with websockets.connect(uri, open_timeout=5) as ws:
            req = {"action": action, "params": params or {}}
            req["params"]["requestId"] = f"cli-{int(__import__('time').time())}"
            await ws.send(json.dumps(req))
            print(f"Sent: {json.dumps(req)}")
            try:
                resp = await asyncio.wait_for(ws.recv(), timeout=15)
                obj = json.loads(resp)
                print(f"Response: {json.dumps(obj, indent=2, ensure_ascii=False)[:2000]}")
                if save_screenshot and action == "screenshot":
                    result = obj.get("result", "")
                    if isinstance(result, str) and result.startswith("data:image/png;base64,"):
                        b64 = result[len("data:image/png;base64,"):]
                        img_bytes = base64.b64decode(b64)
                        with open(save_screenshot, "wb") as f:
                            f.write(img_bytes)
                        print(f"Screenshot saved to: {save_screenshot} ({len(img_bytes)} bytes)")
            except asyncio.TimeoutError:
                print("Timeout waiting for response.")
    except Exception as e:
        print(f"Error: {e}")


def main():
    parser = argparse.ArgumentParser(description="WS client for MCP server testing")
    parser.add_argument("--action", help="Action to send (ping, screenshot, click, etc.)")
    parser.add_argument("--params", help="JSON params", default="{}")
    parser.add_argument("--save", help="Save screenshot to file", default=None)
    args = parser.parse_args()

    if args.action:
        try:
            params = json.loads(args.params)
        except Exception:
            params = {}
        asyncio.run(ws_send_action(args.action, params, args.save))
    else:
        asyncio.run(ws_interactive())


if __name__ == "__main__":
    main()
