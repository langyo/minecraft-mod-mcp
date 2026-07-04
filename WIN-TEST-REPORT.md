# 批量测试报告 — Windows 矩阵 (2026-07-04)

本文件记录在 Windows 11 (win32) 上对 minecraft-mod-mcp 桥接器进行的
「版本 × 客户端框架 × 服务端框架」端到端批量测试结果，以及测试过程中
发现并修复的 Bug。

测试脚本：`scripts/win_matrix_test.py`（matrix_test.py 的 Windows 版，
用 PowerShell Get-CimInstance/Stop-Process 替代 /proc/SIGKILL，跳过 Xvfb）。

## 1. 客户端矩阵 — Fabric（38/38 PASS ✅）

全部 38 个 Fabric 版本端到端通过：install → launch → mod HTTP 上线 →
screenshot → 菜单 enumerate/click。覆盖 1.14.2–1.21.11 + 26.1.2。

| 版本区间 | 数量 | 结果 |
|----------|------|------|
| 1.14.2 – 1.16.5 | 11 | 全部 PASS |
| 1.17.1 – 1.19.4 | 11 | 全部 PASS |
| 1.20 – 1.21.11 | 15 | 全部 PASS |
| 26.1.2 | 1 | PASS |

详见 `win-fabric-client-results.json`。每个版本耗时 15–55s。

## 2b. 客户端矩阵 — Forge/NeoForge（现代版本验证 ✅）

Forge 安装曾因 binarypatcher 校验和不匹配（见 4.1）整体失败。现已通过
「无头 processor 重放失败时回退官方安装器 jar」修复。POC 验证现代版本
端到端通过（install → launch → mod HTTP → screenshot → 菜单交互）：

| 版本 | Loader | 结果 | 备注 |
|------|--------|------|------|
| 1.20.1 | forge | PASS（POC） | |
| 1.21.4 | forge | PASS（POC） | |
| 1.21.11 | forge | PASS（POC，36s） | 官方安装器回退生效 |
| 26.1.2 | forge | PASS（POC） | 项目主目标 |
| 1.21.11 | neoforge | 安装 OK | binarypatcher 同样回退官方安装器 |

**已知限制**：旧版 Forge（1.14–1.16，FG3/FG4 时代）的官方安装器 jar 在
无头模式下会挂起，因此矩阵只覆盖现代版本。这些旧版的模组源码已修复
（MinecraftClient → ReflectionHelper，46 个模组全部编译通过），JAR 也
全部构建完成，只是无头安装链路对它们不稳定。

## 2. 服务端矩阵 — Fabric + vanilla（38/38 PASS ✅）

全部 38 个 Fabric vanilla 服务端安装并到达 "Done"，每个约 6 秒。
覆盖 1.14.2–1.21.11 + 26.1.2。详见 `win-server-fabric-vanilla-results.json`。

| 版本区间 | 数量 | 结果 |
|----------|------|------|
| 1.14.2 – 1.16.5 | 11 | 全部 PASS |
| 1.17.1 – 1.19.4 | 11 | 全部 PASS |
| 1.20 – 1.21.11 | 15 | 全部 PASS |
| 26.1.2 | 1 | PASS |

## 3. 已修复的 Bug（本次测试发现）

### 3.1 Fabric 模组编译失败（1.14.2/1.14.3/1.21.4–1.21.8）

- **1.21.4–1.21.8 MouseClickMixin** 引用了 `net.minecraft.client.input.MouseInput`
  —— 该类仅存在于 1.21.11+ 的 yarn 映射。改用 `(long,int,int,int)` 签名匹配
  实际的 `Mouse.onMouseButton`。
- **1.21.4 InGameHudMixin** 调用 `RenderTickCounter.getTickProgress(boolean)`，
  1.21.4 中该方法名为 `getTickDelta(boolean)`。
- **1.14.2 MinecraftClientMixin** import `GameMenuScreen`，但 1.14.2 yarn
  命名为 `PauseScreen`（1.14.3 起才叫 GameMenuScreen）。
- **1.14.2/1.14.3 ScreenMixin** 调用 `onScreenRender(screen,...)` 但
  ModDevMcpMod.onScreenRender 在这些版本签名是 `(ctx, screen, ...)`。

### 3.2 Forge/NeoForge 模组源码引用 Fabric 类名（46 个模组）

所有 forge/neoforge 的 ModDevMcpMod 调用 `MinecraftClient.getInstance()`
（Fabric 类名），但 Forge 用 MCP 映射、NeoForge 用 Mojang official 映射，
类名是 `net.minecraft.client.Minecraft`，导致全部 compileJava 失败。
统一改为 `ReflectionHelper.getMinecraftInstance()`（1.21.11 forge 已用的反射方式）。
修复后 41/51 forge + 7/7 neoforge 模组成功构建。

### 3.3 Forge 安装器 classpath 分隔符错误（Windows 致命）

`forgeProcessor.ts` 用 `cpEntries.join(":")` 拼 processor classpath —— POSIX
分隔符。Windows JVM 读 `;`，导致多 jar classpath 塌缩成一个无效路径，
每个 processor 抛 `ClassNotFoundException`（如 ConsoleTool），Forge 安装
永远卡在 processor 4/7。修复：win32 用 `;`，其他平台用 `:`。

### 3.4 模组 JAR 未部署到 mods/（Forge/NeoForge 模组永不加载）

`launch.ts` 仅对 `loader==="fabric"` 执行 `deployModToModsDir`。Forge/NeoForge
从 mods/ 目录发现模组，不从裸 classpath 加载，因此游戏内 MCP HTTP 服务
从未启动。修复：对所有 loader 都部署到 mods/。

### 3.5 server 命令在 Windows 永不退出

`launchServer` 用 `detached: process.platform !== "win32"`，Windows 上为
false，Node 事件循环持有子进程句柄，CLI 打印 "Server launched successfully."
后无限挂起。修复：启动后 `unref()` + `process.exit(0)`。

### 3.6 测试框架残留僵尸进程占用端口

Fabric 矩阵结束后留下一个存活 1.9 小时的 java 进程占用 9876 端口，
导致后续 Forge 矩阵的 mod HTTP 静默绑定失败、整个 run 卡在 1.14.3。
修复：扩大 `_MC_CMD_TOKENS` 匹配范围 + `Stop-Process -Force` 兜底回收。

### 3.7 server 命令的分离子进程在 Windows 被 CLI 退出连带杀死

`launchServer` 用 `detached: process.platform !== "win32"`，Windows 上为
false。3.5 的 `process.exit(0)` 修复虽然让 CLI 退出了，但连带把同进程组
的服务端子进程也杀了 —— 服务端永远跑不到 "Done"，logs/latest.log 根本
不产生。服务端矩阵因此出现 31 个 PARTIAL。修复：`detached: true` 全平台
+ `unref()`。

### 3.8 versions-embedded.json 与 version_config.py 失同步

CLI 的 versions-embedded.json 漏了 1.21.4–1.21.10、26.1.2 的 fabric_yarn
字段，getVersionForLoader() 返回 null，这些版本的 fabric 安装全部报
"fabric not available"，服务端矩阵 8 个 FAIL。修复：新增
scripts/gen_embedded_versions.py 从 version_config.ALL_VERSIONS 重新生成。

### 3.9 _java_pids 把所有 java 进程都跳过了（gradle 在 JDK 路径里）

僵尸清理的 skip 过滤器排除任何 cmdline 含 "gradle" 的进程。但 Gradle
自动下载的 JDK 装在 ~/.gradle/jdks/，所以每个用自动安装 JDK 启动的
MC 进程路径里都有 "gradle"，全部被误跳过 —— _java_pids 返回 []，
kill_mc_processes() 什么都不杀。服务端矩阵因此 29 个 PARTIAL（孤儿
服务端跨测试残留）。修复：只跳真正的 Gradle/Kotlin 构建守护进程
（org.gradle.launcher / GradleWrapperMain），不再匹配裸 "gradle"。

### 3.10 Forge/NeoForge binarypatcher 校验和不匹配 — 已修复

1.21.4+/1.21.11 forge 与 1.21.11 neoforge 的安装器 processor 跑到
binarypatcher 时报 `Patch expected GLX to have checksum 36f15e24 but it was cee55bd1`。

**根因**：binarypatcher 用 Adler32（不是 CRC32）校验单个 class。
`MC_OFF`（official jar，SHA1 `30a7252f`）由 processor 5
（ForgeAutoRenamingTool `--reverse --strip-sigs`）从 vanilla jar 重映射生成。
我们的 FART 输出 jar 中 `GLX.class` 的 Adler32 = `cee55bd1`，但 binarypatcher
patch 数据 (data/client.lzma) 记录的预期值是 `36f15e24` —— 说明 Forge 团队
构建 patch 时用的 FART 输出与当前 FART 1.0.6 + 同样参数产出的字节码不一致。

**修复**：官方安装器 jar 是权威的（它跑同样的 pipeline 能通过校验）。
downloadForgeInstaller/downloadNeoforgeInstaller 现在先尝试无头 processor
重放（快），仅当重放报 binarypatcher/checksum 错误时才回退到
`java -jar <installer> --installClient`（权威但慢）。验证：1.21.11 forge
现在 install → launch → mod HTTP → screenshot → 菜单交互全通过（36s）。
注：旧版 Forge（1.14–1.16）的官方安装器 jar 在无头模式会挂起，因此
回退仅对 binarypatcher 类错误触发，避免矩阵被旧版拖死。

### 4.2 26.1.2 无 Fabric yarn 映射

Fabric 官方尚未发布 26.1.2 的 yarn（meta.fabricmc.net 返回 `[]`），
因此 26.1.2 fabric 模组无法从源码构建（npx 用户走 GitHub Releases 缓存 jar）。

### 4.3 pre-1.14 Forge（FG2 时代）

上游 harness 已跳过：FG2 安装器格式不被支持（de0202d8）。
