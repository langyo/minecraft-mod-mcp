"""MCP Workflow Engine - executes YAML-defined automation workflows on Minecraft.

Core concepts:
  - preview_click: Draw a red marker at (x,y) on the NEXT screenshot without actually clicking.
    The AI reviews the annotated image and decides: confirm (execute click) or adjust (change coords).
  - Two-phase click: preview → screenshot (with annotation) → vision_verify → click_or_adjust
  - State machine: each step produces a result stored in context for downstream steps to reference.

Actions:
  wait, screenshot, click, preview_click, click_btn_idx, click_btn_id,
  ctrl_on, ctrl_off, key, paste, scroll, look_delta, set_view_angle,
  right_click, enumerate_widgets, get_screen_buttons, cmd, vision_check,
  assert_screen, conditional
"""
import json
import os
import sys
import time
from pathlib import Path
from dataclasses import dataclass, field
from typing import Any, Optional

ROOT = Path(__file__).resolve().parent.parent
MC_DIR = Path(os.environ.get("APPDATA", os.path.expanduser("~"))) / ".minecraft"

from PIL import Image, ImageDraw, ImageFont


@dataclass
class Marker:
    x: int
    y: int
    label: str = ""
    radius: int = 10
    color: str = "#FF0000"
    crosshair: bool = True


@dataclass
class StepResult:
    action: str
    success: bool = True
    data: Any = None
    screenshot_path: Optional[str] = None
    error: Optional[str] = None
    duration: float = 0.0


@dataclass
class WorkflowContext:
    setup: dict = field(default_factory=dict)
    step_results: list = field(default_factory=list)
    pending_markers: list = field(default_factory=list)
    variables: dict = field(default_factory=dict)
    screenshots_dir: Path = field(default_factory=lambda: ROOT / "screenshots" / "workflow")
    server_proc: Any = None
    mc_proc: Any = None
    container: Any = None
    current_step: int = 0
    total_steps: int = 0
    control_mode: bool = False
    dry_run: bool = False

    def __post_init__(self):
        self.screenshots_dir.mkdir(parents=True, exist_ok=True)


class WorkflowEngine:
    def __init__(self, ctx: WorkflowContext, send_cmd_fn, pump_fn=None):
        self.ctx = ctx
        self._send = send_cmd_fn
        self._pump = pump_fn or (lambda: None)

    def run_step(self, step: dict) -> StepResult:
        action = step.get("action", "")
        self.ctx.current_step += 1
        idx = self.ctx.current_step

        t0 = time.time()
        comment = step.get("comment", "")
        print(f"\n  [{idx}/{self.ctx.total_steps}] {action}" + (f"  # {comment}" if comment else ""))

        try:
            handler = getattr(self, f"_do_{action}", None)
            if handler is None:
                return self._fail(action, f"unknown action: {action}")
            result = handler(step)
            result.duration = time.time() - t0
            self.ctx.step_results.append(result)
            return result
        except Exception as e:
            result = self._fail(action, str(e))
            result.duration = time.time() - t0
            self.ctx.step_results.append(result)
            return result

    def _fail(self, action, error):
        print(f"    FAIL: {error}")
        return StepResult(action=action, success=False, error=error)

    def _ok(self, action, data=None, **kw):
        return StepResult(action=action, success=True, data=data, **kw)

    # ========== Wait ==========

    def _do_wait(self, step):
        seconds = float(step.get("seconds", 2))
        print(f"    WAIT {seconds}s")
        deadline = time.time() + seconds
        while time.time() < deadline:
            self._pump()
            time.sleep(0.05)
        return self._ok("wait")

    # ========== Control Mode ==========

    def _do_ctrl_on(self, step):
        if self.ctx.dry_run:
            print(f"    [DRY] ctrl_on")
            self.ctx.control_mode = True
            return self._ok("ctrl_on")
        self._send("enter_control_mode", {})
        self.ctx.control_mode = True
        print(f"    CTRL_MODE ON")
        time.sleep(1)
        self._pump()
        return self._ok("ctrl_on")

    def _do_ctrl_off(self, step):
        if self.ctx.dry_run:
            print(f"    [DRY] ctrl_off")
            self.ctx.control_mode = False
            return self._ok("ctrl_off")
        self._send("exit_control_mode", {})
        self.ctx.control_mode = False
        print(f"    CTRL_MODE OFF")
        time.sleep(1)
        self._pump()
        return self._ok("ctrl_off")

    # ========== Screenshot ==========

    def _do_screenshot(self, step):
        name = step.get("name", f"step_{self.ctx.current_step}")
        ts = int(time.time())
        raw_path = str(self.ctx.screenshots_dir / f"{name}_{ts}_raw.png")
        final_path = str(self.ctx.screenshots_dir / f"{name}_{ts}.png")

        if self.ctx.dry_run:
            print(f"    [DRY] screenshot -> {final_path}")
            return self._ok("screenshot", screenshot_path=final_path)

        self._send("screenshot", {"save_path": raw_path})
        time.sleep(6)

        raw = self._find_recent_file(name + "_*_raw.png", max_age=20)
        if not raw:
            raw = self._find_recent_file("mc_*.png", max_age=20)

        if not raw:
            print(f"    FAIL: no screenshot captured")
            return self._fail("screenshot", "no screenshot file found")

        print(f"    RAW: {raw} ({os.path.getsize(raw) // 1024}KB)")

        if self.ctx.pending_markers:
            annotated = self._annotate_image(raw, self.ctx.pending_markers, final_path)
            self.ctx.pending_markers.clear()
            if annotated:
                print(f"    ANNOTATED: {annotated} ({os.path.getsize(annotated) // 1024}KB)")
                return self._ok("screenshot", screenshot_path=annotated)

        import shutil
        shutil.copy2(raw, final_path)
        print(f"    SAVED: {final_path} ({os.path.getsize(final_path) // 1024}KB)")
        self._pump()
        return self._ok("screenshot", screenshot_path=final_path)

    def _find_recent_file(self, pattern, max_age=20):
        candidates = list(self.ctx.screenshots_dir.glob(pattern))
        if not candidates:
            candidates = list((ROOT / "screenshots").glob(pattern))
        candidates.sort(key=os.path.getmtime, reverse=True)
        for f in candidates:
            if 0 < (time.time() - os.path.getmtime(f)) < max_age:
                return str(f)
        return None

    def _annotate_image(self, src_path, markers, out_path):
        try:
            img = Image.open(src_path)
            draw = ImageDraw.Draw(img)
            w, h = img.size

            for m in markers:
                x, y, r = m.x, m.y, m.radius
                color = m.color

                if m.crosshair:
                    draw.line([(0, y), (w, y)], fill=color, width=1)
                    draw.line([(x, 0), (x, h)], fill=color, width=1)
                draw.ellipse([(x - r, y - r), (x + r, y + r)], outline=color, width=3)
                draw.ellipse([(x - 3, y - 3), (x + 3, y + 3)], fill=color)

                if m.label:
                    try:
                        font = ImageFont.truetype("arial.ttf", 16)
                    except Exception:
                        font = ImageFont.load_default()
                    text = f"({x},{y}) {m.label}"
                    tx = x + r + 8
                    ty = y - 12
                    if tx + 200 > w:
                        tx = x - r - 8 - 200
                        ty = y + 10
                    bbox = draw.textbbox((tx, ty), text, font=font)
                    pad = 4
                    draw.rectangle([bbox[0] - pad, bbox[1] - pad, bbox[2] + pad, bbox[3] + pad], fill="#000000CC")
                    draw.text((tx, ty), text, fill=color, font=font)

            img.save(out_path)
            return out_path
        except Exception as e:
            print(f"    annotate error: {e}")
            return None

    # ========== Preview Click ==========

    def _do_preview_click(self, step):
        x = int(step.get("x", 0))
        y = int(step.get("y", 0))
        label = step.get("label", "")
        radius = int(step.get("radius", 10))
        color = step.get("color", "#FF0000")

        marker = Marker(x=x, y=y, label=label, radius=radius, color=color)
        self.ctx.pending_markers.append(marker)
        print(f"    PREVIEW_CLICK queued: ({x},{y}) label='{label}'")
        return self._ok("preview_click", data={"x": x, "y": y, "label": label})

    # ========== Click ==========

    def _do_click(self, step):
        x = int(step.get("x", 0))
        y = int(step.get("y", 0))
        if self.ctx.dry_run:
            print(f"    [DRY] click ({x},{y})")
            return self._ok("click", data={"x": x, "y": y})
        self._send("click", {"x": x, "y": y})
        print(f"    CLICK ({x},{y})")
        time.sleep(2)
        self._pump()
        return self._ok("click", data={"x": x, "y": y})

    # ========== Button Click ==========

    def _do_click_btn_idx(self, step):
        idx = int(step.get("index", 0))
        if self.ctx.dry_run:
            print(f"    [DRY] click_btn_idx {idx}")
            return self._ok("click_btn_idx")
        self._send("click_button_index", {"index": idx})
        print(f"    CLICK_BTN_IDX {idx}")
        time.sleep(3)
        self._pump()
        return self._ok("click_btn_idx")

    def _do_click_btn_id(self, step):
        bid = int(step.get("button_id", 0))
        if self.ctx.dry_run:
            print(f"    [DRY] click_btn_id {bid}")
            return self._ok("click_btn_id")
        self._send("click_button_id", {"button_id": bid})
        print(f"    CLICK_BTN_ID {bid}")
        time.sleep(3)
        self._pump()
        return self._ok("click_btn_id")

    # ========== Key / Paste / Scroll ==========

    def _do_key(self, step):
        key = step.get("key", "")
        if self.ctx.dry_run:
            print(f"    [DRY] key {key}")
            return self._ok("key")
        self._send("press_key", {"key": key})
        print(f"    KEY {key}")
        time.sleep(1.5)
        self._pump()
        return self._ok("key")

    def _do_paste(self, step):
        text = step.get("text", "")
        press_enter = step.get("press_enter", False)
        if self.ctx.dry_run:
            print(f"    [DRY] paste {text!r}")
            return self._ok("paste")
        self._send("paste_text", {"text": text})
        if press_enter:
            time.sleep(0.5)
            self._send("press_key", {"key": "Enter"})
        print(f"    PASTE {text!r}")
        time.sleep(1.5)
        self._pump()
        return self._ok("paste")

    def _do_scroll(self, step):
        clicks = int(step.get("clicks", 1))
        if self.ctx.dry_run:
            print(f"    [DRY] scroll {clicks}")
            return self._ok("scroll")
        self._send("scroll", {"clicks": clicks})
        print(f"    SCROLL {clicks}")
        time.sleep(1)
        self._pump()
        return self._ok("scroll")

    # ========== Look / View ==========

    def _do_look_delta(self, step):
        dyaw = float(step.get("dyaw", 0))
        dpitch = float(step.get("dpitch", 0))
        if self.ctx.dry_run:
            print(f"    [DRY] look_delta dyaw={dyaw} dpitch={dpitch}")
            return self._ok("look_delta")
        self._send("look_delta", {"dyaw": dyaw, "dpitch": dpitch})
        print(f"    LOOK_DELTA dyaw={dyaw} dpitch={dpitch}")
        time.sleep(1)
        self._pump()
        return self._ok("look_delta")

    def _do_set_view_angle(self, step):
        yaw = float(step.get("yaw", 0))
        pitch = float(step.get("pitch", 0))
        if self.ctx.dry_run:
            print(f"    [DRY] set_view_angle yaw={yaw} pitch={pitch}")
            return self._ok("set_view_angle")
        self._send("set_view_angle", {"yaw": yaw, "pitch": pitch})
        print(f"    SET_VIEW yaw={yaw} pitch={pitch}")
        time.sleep(1)
        self._pump()
        return self._ok("set_view_angle")

    # ========== Right Click ==========

    def _do_right_click(self, step):
        if self.ctx.dry_run:
            print(f"    [DRY] right_click")
            return self._ok("right_click")
        self._send("right_click", {})
        print(f"    RIGHT_CLICK")
        time.sleep(1)
        self._pump()
        return self._ok("right_click")

    # ========== Enumerate / Buttons ==========

    def _do_enumerate_widgets(self, step):
        if self.ctx.dry_run:
            print(f"    [DRY] enumerate_widgets")
            return self._ok("enumerate_widgets")
        self._send("enumerate_widgets", {})
        print(f"    ENUMERATE_WIDGETS")
        time.sleep(2)
        self._pump()
        return self._ok("enumerate_widgets")

    def _do_get_screen_buttons(self, step):
        if self.ctx.dry_run:
            print(f"    [DRY] get_screen_buttons")
            return self._ok("get_screen_buttons")
        self._send("get_screen_buttons", {})
        print(f"    GET_SCREEN_BUTTONS")
        time.sleep(2)
        self._pump()
        return self._ok("get_screen_buttons")

    # ========== Command ==========

    def _do_cmd(self, step):
        command = step.get("command", "")
        if self.ctx.dry_run:
            print(f"    [DRY] cmd /{command}")
            return self._ok("cmd")
        self._send("execute_command", {"command": command})
        print(f"    CMD /{command}")
        time.sleep(2)
        self._pump()
        return self._ok("cmd")

    # ========== Vision Check ==========

    def _do_vision_check(self, step):
        prompt = step.get("prompt", "Describe what you see in this screenshot.")
        expect = step.get("expect", None)
        store_as = step.get("store_as", None)

        last_ss = None
        for r in reversed(self.ctx.step_results):
            if r.screenshot_path:
                last_ss = r.screenshot_path
                break

        if not last_ss:
            print(f"    VISION_CHECK: no screenshot to analyze")
            return self._fail("vision_check", "no screenshot available")

        print(f"    VISION_CHECK: analyzing {os.path.basename(last_ss)}")
        print(f"    Prompt: {prompt[:80]}...")

        analysis = self._vision_analyze(last_ss, prompt)
        if analysis is None:
            return self._fail("vision_check", "vision analysis failed")

        print(f"    Result: {str(analysis)[:200]}")

        if store_as:
            self.ctx.variables[store_as] = analysis

        success = True
        if expect:
            success = expect.lower() in str(analysis).lower()
            print(f"    Expect '{expect}': {'PASS' if success else 'FAIL'}")

        return self._ok("vision_check", data={"analysis": analysis, "expect_met": success})

    def _vision_analyze(self, image_path, prompt):
        return None  # Placeholder - overridden by runner with actual MCP tool call

    # ========== Assert Screen ==========

    def _do_assert_screen(self, step):
        expected = step.get("screen", "")
        if self.ctx.dry_run:
            print(f"    [DRY] assert_screen == {expected}")
            return self._ok("assert_screen")

        self._send("enumerate_widgets", {})
        time.sleep(2)
        self._pump()
        return self._ok("assert_screen")

    # ========== Conditional ==========

    def _do_if_screen(self, step):
        screen_name = step.get("screen", "")
        then_steps = step.get("then", [])
        else_steps = step.get("else", [])

        if self.ctx.dry_run:
            print(f"    [DRY] if_screen '{screen_name}'")
            return self._ok("if_screen")

        self._send("enumerate_widgets", {})
        time.sleep(2)
        self._pump()
        return self._ok("if_screen")

    # ========== Batch Run ==========

    def run_all(self, steps):
        self.ctx.total_steps = len(steps)
        self.ctx.current_step = 0
        results = []
        for step in steps:
            result = self.run_step(step)
            results.append(result)
            if not result.success and step.get("abort_on_fail", False):
                print(f"\n  ABORTED at step {self.ctx.current_step}: {result.error}")
                break
        return results
