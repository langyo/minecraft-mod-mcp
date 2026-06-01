"""Unified CLI logger for Entelecheia.

Matches the Rust-side log format exactly:
    [  OK  ] message       (green, bold)
    [ INFO ] message       (cyan)
    [ WARN ] message       (yellow, bold)
    [ FAIL ] message       (red, bold)
    [  ..  ] message       (dim, pending/in-progress)

Rules:
  - No divider lines. Ever.
  - Tag width is fixed at 8 chars: ``[ XXXXX ]``.
  - ``NO_COLOR`` and ``TERM=dumb`` are respected.
  - All Python code in this repo should use this module for output.
"""

import os
import sys
import time

_BRIGHT_GREEN = "\033[1;32m"
_BRIGHT_RED = "\033[1;31m"
_BRIGHT_YELLOW = "\033[1;33m"
_BRIGHT_CYAN = "\033[1;36m"
_DIM = "\033[2m"
_BOLD = "\033[1m"
_NC = "\033[0m"

_on: bool | None = None


def _color_on() -> bool:
    global _on
    if _on is None:
        _on = (
            hasattr(sys.stdout, "isatty")
            and sys.stdout.isatty()
            and not os.environ.get("NO_COLOR")
            and os.environ.get("TERM", "") != "dumb"
        )
    return _on


def _c(code: str, text: str) -> str:
    return f"{code}{text}{_NC}" if _color_on() else text


def ok(msg: str):
    print(f"{_c(_BRIGHT_GREEN, '[  OK  ]')} {msg}")


def info(msg: str):
    print(f"{_c(_BRIGHT_CYAN, '[ INFO ]')} {msg}")


def warn(msg: str):
    print(f"{_c(_BRIGHT_YELLOW, '[ WARN ]')} {msg}")


def fail(msg: str):
    print(f"{_c(_BRIGHT_RED, '[ FAIL ]')} {msg}", file=sys.stderr)


def pending(msg: str):
    print(f"{_c(_DIM, '[  ..  ]')} {msg}")


def bold(msg: str):
    print(_c(_BOLD, msg))


def blank():
    print()


def elapsed(label: str, seconds: float):
    if seconds < 60:
        ok(f"{label} ({seconds:.1f}s)")
    else:
        m, s = divmod(seconds, 60)
        ok(f"{label} ({int(m)}m {s:.0f}s)")


class ProgressTimer:
    def __init__(self, label: str):
        self._label = label
        self._start = time.monotonic()

    def done(self):
        self.elapsed_secs = time.monotonic() - self._start
        elapsed(self._label, self.elapsed_secs)
        return self.elapsed_secs

    def fail(self, reason: str = ""):
        self.elapsed_secs = time.monotonic() - self._start
        msg = f"{self._label} ({self.elapsed_secs:.1f}s)"
        if reason:
            msg += f" — {reason}"
        fail(msg)
        return self.elapsed_secs


def section(title: str):
    blank()
    bold(title)


# ── Legacy compat (used by scripts/ that import cli_format directly) ──

def separator():
    pass


def header(title: str):
    section(title)


def step(title: str):
    bold(f"==> {title}")


err = fail
