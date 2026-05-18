"""Run a YAML-defined MCP automation workflow on Minecraft.

Usage:
  python scripts/run_yaml.py workflows/smoke_test.yaml
  python scripts/run_yaml.py workflows/smoke_test.yaml --dry-run
  python scripts/run_yaml.py workflows/smoke_test.yaml --skip-setup  # MC already running
  python scripts/run_yaml.py workflows/smoke_test.yaml --step 5      # run only step 5
  python scripts/run_yaml.py workflows/smoke_test.yaml --interactive  # pause for review between steps

The YAML format supports these actions:
  wait, screenshot, click, preview_click, click_btn_idx, click_btn_id,
  ctrl_on, ctrl_off, key, paste, scroll, look_delta, set_view_angle,
  right_click, enumerate_widgets, get_screen_buttons, cmd, vision_check

Preview Click Flow:
  1. preview_click: queues a red marker at (x,y)
  2. screenshot: captures frame AND draws the queued marker on the saved image
  3. (AI operator reviews the annotated screenshot)
  4. click: executes the actual click at confirmed coordinates
  5. If marker was wrong, adjust x/y and go back to step 1
"""
import argparse
import json
import os
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCRIPTS = ROOT / "scripts"
MC_DIR = Path(os.environ.get("APPDATA", os.path.expanduser("~"))) / ".minecraft"

sys.path.insert(0, str(SCRIPTS))

try:
    import yaml
except ImportError:
    print("ERROR: PyYAML not installed. Run: pip install pyyaml")
    sys.exit(1)

from workflow_engine import WorkflowEngine, WorkflowContext
from test_version import (
    kill_all_java, _start_mcp_server, _send_server_cmd,
    _start_mc, clear_mods, install_mod
)
from container import McpContainer


def load_workflow(yaml_path):
    with open(yaml_path, "r", encoding="utf-8") as f:
        return yaml.safe_load(f)


def connect_mc(mc_proc, timeout=120):
    deadline = time.time() + timeout
    while time.time() < deadline:
        if mc_proc and mc_proc.poll() is not None:
            return False
        log = MC_DIR / "mcp-launch-stdout.log"
        if log.exists():
            try:
                if "MCP-WS" in log.read_text(encoding="utf-8", errors="replace"):
                    return True
            except Exception:
                pass
        time.sleep(3)
    return False


def main():
    parser = argparse.ArgumentParser(description="Run YAML MCP workflow")
    parser.add_argument("workflow", help="Path to YAML workflow file")
    parser.add_argument("--dry-run", action="store_true", help="Don't actually send commands")
    parser.add_argument("--skip-setup", action="store_true", help="MC already running, skip launch")
    parser.add_argument("--step", type=int, help="Run only this step number (1-indexed)")
    parser.add_argument("--interactive", action="store_true", help="Pause for review between steps")
    parser.add_argument("--no-container", action="store_true", help="Don't use container embedding")
    args = parser.parse_args()

    wf = load_workflow(args.workflow)
    name = wf.get("name", Path(args.workflow).stem)
    desc = wf.get("description", "").strip()
    setup = wf.get("setup", {})
    steps = wf.get("steps", [])

    version = setup.get("version", "1.21.7-forge-57.0.2")
    use_container = setup.get("container", True) and not args.no_container
    wait_connect = setup.get("wait_after_connect", 15)

    print("=" * 60)
    print(f"WORKFLOW: {name}")
    if desc:
        for line in desc.split("\n"):
            print(f"  {line}")
    print(f"  Version: {version}")
    print(f"  Steps: {len(steps)}")
    print(f"  Dry-run: {args.dry_run}")
    print("=" * 60)

    server_proc = None
    mc_proc = None
    container = None

    ctx = WorkflowContext(
        setup=setup,
        dry_run=args.dry_run,
    )

    try:
        if not args.skip_setup:
            print("\n[SETUP] Killing old processes...")
            kill_all_java()
            time.sleep(2)

            print("[SETUP] Starting MCP server...")
            server_proc = _start_mcp_server()
            time.sleep(3)
            ctx.server_proc = server_proc

            print(f"[SETUP] Installing mod and launching MC {version}...")
            clear_mods()
            install_mod(version, "forge")
            mc_proc = _start_mc(version)
            ctx.mc_proc = mc_proc
            print(f"  MC pid={mc_proc.pid}")

            if use_container:
                print("[SETUP] Creating container window...")
                container = McpContainer()
                container.create(900, 600, f"MCP - {name}")
                try:
                    container.embed_mc(mc_proc.pid, timeout=30)
                    print(f"  MC embedded! (hwnd={container.mc_hwnd:#x})")
                except RuntimeError as e:
                    print(f"  Embed failed: {e}")
                    container.destroy()
                    container = None
            ctx.container = container

            print("[SETUP] Waiting for mod connection...")
            if not connect_mc(mc_proc):
                print("  FAIL: MC did not connect")
                return 1
            print("  CONNECTED!")

            print(f"[SETUP] Waiting {wait_connect}s for MC to fully load...")
            time.sleep(wait_connect)
        else:
            print("[SETUP] Skipped (--skip-setup)")

        def send_cmd(tool, tool_args):
            if server_proc and server_proc.poll() is None:
                _send_server_cmd(server_proc, tool, tool_args)

        def pump():
            if container:
                container.pump_messages()

        engine = WorkflowEngine(ctx, send_cmd, pump)

        target_steps = steps
        if args.step is not None:
            if 1 <= args.step <= len(steps):
                target_steps = [steps[args.step - 1]]
            else:
                print(f"Invalid step {args.step}, valid range: 1-{len(steps)}")
                return 1

        results = engine.run_all(target_steps)

        passed = sum(1 for r in results if r.success)
        failed = sum(1 for r in results if not r.success)

        print("\n" + "=" * 60)
        print(f"RESULT: {passed} passed, {failed} failed")
        for i, r in enumerate(results):
            status = "OK" if r.success else "FAIL"
            extra = f" -> {r.screenshot_path}" if r.screenshot_path else ""
            extra += f" ({r.error})" if r.error else ""
            print(f"  [{i + 1}] {r.action}: {status}{extra}")
        print("=" * 60)

        return 1 if failed > 0 else 0

    except KeyboardInterrupt:
        print("\nInterrupted!")
        return 130
    except Exception as e:
        print(f"\nERROR: {e}")
        import traceback
        traceback.print_exc()
        return 1
    finally:
        if ctx.control_mode and server_proc:
            try:
                _send_server_cmd(server_proc, "exit_control_mode", {})
            except Exception:
                pass
        if container:
            container.destroy()
        if server_proc:
            try:
                server_proc.stdin.close()
            except Exception:
                pass
            server_proc.kill()
        if mc_proc and mc_proc.poll() is None:
            mc_proc.kill()
        kill_all_java()


if __name__ == "__main__":
    sys.exit(main())
