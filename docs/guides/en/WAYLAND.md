# Wayland and Hyprland Troubleshooting

This guide covers practical issues found while controlling Minecraft Mod MCP
on a native Wayland desktop. Commands use Hyprland, Prism Launcher, and Linux,
but the process and input caveats apply to other Wayland compositors and
external launchers.

## Verify the client with multiple signals

`get_minecraft_status().processAlive` reports whether a process started by
this bridge is alive. It is expected to be `false` when Minecraft was launched
by Prism or another external launcher. In that case, use `connected` together
with the reported mod `pid`.

The status tool probes the selected mod port before responding and clears a
stale connection after a failed heartbeat. Still verify an external client
with several independent signals when diagnosing launcher or compositor
problems:

1. Find the real Java PID.
2. Confirm the PID with `ps`.
3. Call `ping`.
4. Read `get_player_info`.
5. Capture a screenshot or inspect the current GUI.
6. Check that Minecraft's `latest.log` has current timestamps.

The status result includes the mod's real `pid`, `uptime`, `version`, and
`loader`. Prefer those values over matching the first Java process on the
machine.

## Find the Wayland window

Wayland does not expose X11-style global window control. Under Hyprland, use
compositor IPC:

```bash
hyprctl clients -j | jq -c '.[] | {
  address, pid, class, title, workspace, focusHistoryID
}'
```

Minecraft's window class may be empty during startup or in some LWJGL states.
Match both PID and title. Re-resolve them after every restart, because stale
Prism and Java processes may coexist.

Do not select the first process named `java`; Gradle daemons and dedicated
servers are Java processes too. One useful diagnostic is:

```bash
ps -eo pid,ppid,stat,lstart,cmd | rg \
  '/java.*(org\.prismlauncher\.EntryPoint|minecraft)'
```

## Understand Prism's process model

Prism is normally single-instance. A new AppImage invocation can forward a
launch request to an existing Prism process and then exit. The existing Prism
process creates the Java child.

This has several consequences:

- `$!` from the AppImage command may not be Minecraft's PID.
- Waiting on the AppImage invocation may not capture Minecraft's exit status.
- Redirected AppImage output may contain only Qt frontend warnings.
- You must discover and monitor the new Java child after launch.

For example:

```bash
PrismLauncher.AppImage --appimage-extract-and-run \
  --launch "INSTANCE_ID" --server example.invalid
```

This warning concerns Prism's Qt frontend and does not by itself prove a
Minecraft or LWJGL failure:

```text
qt.qpa.plugin: Could not find the Qt platform plugin "wayland"
```

Prism may run through XWayland while Minecraft uses a separate GLFW window.

## Know what `press_key` injects

On modern Minecraft versions, `press_key` invokes Minecraft's keyboard
handler inside the process. It is not an operating-system keyboard event.
This works for normal game input but may not trigger every loader-specific or
native GLFW event listener used by another mod.

If a test specifically needs a compositor-level key event, Hyprland `0.56`
can target the current Minecraft PID:

```bash
hyprctl dispatch \
  'hl.dsp.send_key_state({mods="", key="Up", state="down", window="pid:PID"})'
sleep 0.2
hyprctl dispatch \
  'hl.dsp.send_key_state({mods="", key="Up", state="up", window="pid:PID"})'
```

This is an advanced fallback, not part of Minecraft Mod MCP. Hyprland command
syntax differs between versions.

When using it:

- Always send distinct down and up edges.
- Never reuse a PID after restart.
- Log the target PID and each injected edge.
- Confirm exactly one resulting state transition in the game log.
- Do not mix compositor injection with a queued `KeyMapping.consumeClick()`
  toggle unless repeated activation is intended.

## Separate cursor release, control mode, and focus behavior

These are different states:

- `release_mouse` releases GLFW's cursor grab.
- MCP control mode enables MCP click, type, and keyboard tools.
- Minecraft's pause-on-focus-loss setting controls behavior after changing
  windows or workspaces.

Releasing the cursor does not itself change Minecraft's pause-on-focus-loss
option. Automation that requires client ticks while unfocused must account for
that separately in the tested mod or Minecraft configuration.

Use control mode only while needed. Status, player state, world state, and
screenshots are safer for passive observation. GUI and keyboard actions may
require control mode and should be followed by server-authoritative
confirmation rather than assuming the local click already changed inventory.

## Switch workspaces safely

Inspect current workspace and Minecraft placement before a test:

```bash
hyprctl activeworkspace -j
hyprctl clients -j | jq -c \
  '.[] | select(.pid == PID) | {pid,title,workspace}'
```

When the test only needs focus loss, prefer a semantic compositor dispatch:

```bash
hyprctl dispatch 'hl.dsp.focus({workspace=2})'
```

This avoids leaking `Super` or number-key state into Minecraft. Use the real
`Super + number` shortcut only when reproducing keyboard-routing behavior.

After switching, verify the same Java PID, call `ping` and `get_player_info`,
and check the game log for unexpected key actions. If an automation toggle
changes state immediately before exit, investigate input routing before
classifying the event as a Wayland rendering crash.

## Diagnose abrupt exits

Minecraft logs cannot explain every external `SIGTERM` or `SIGKILL`. Check all
available layers around the failure time:

```bash
journalctl --since 'TIME-1min' --until 'TIME+1min' --no-pager
journalctl -k --since 'TIME-1min' --until 'TIME+1min' --no-pager
coredumpctl list --no-pager
```

Also inspect:

- Minecraft `logs/latest.log`
- Minecraft `logs/debug.log`
- Prism launcher logs and captured game console
- `hs_err_pid*.log`
- compositor logs

Typical interpretations:

- Java stack trace or loader crash report: game or mod failure.
- `hs_err_pid` file or coredump: JVM, native library, or LWJGL failure.
- kernel OOM entry: memory-pressure kill.
- clean log ending without a shutdown sequence: likely external signal.
- mod stop or toggle immediately before exit: likely input or application
  behavior rather than a compositor crash.

When Prism was already running, monitor the discovered Java child directly;
the forwarding AppImage command cannot provide the game's exit status.

## Test checklist

1. Record compositor, launcher, Minecraft, loader, mod, and Java versions.
2. Launch the intended instance.
3. Discover fresh `/api/status` PID and Hyprland window address.
4. Confirm PID, `ping`, player state, screenshot, and current log timestamps.
5. Enter control mode only if the requested action requires it.
6. Inject one explicit key action and confirm one result.
7. Test semantic workspace switching before real shortcut injection.
8. Re-check PID, MCP calls, and logs after focus changes.
9. Preserve all logs before relaunching after a failure.
10. Never include accounts, access tokens, private server addresses, or chat
    contents in bug reports.
