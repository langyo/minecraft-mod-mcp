# Minecraft Mod MCP — 修复记录

## 测试环境

- 项目: `mcbbs-memorial` (TeaCon 2026)
- 目标版本: **Minecraft 26.1.2 + NeoForge 26.1.2.36-beta**
- Java: 25 (Eclipse Adoptium)
- 系统: Windows 11
- MCP 工具: minecraft-mod-mcp (本地安装)

## 已修复的问题

### 1. Forge/NeoForge 安装器对新版本不可用 — **已修复**

**根因**: 新版 Forge/NeoForge (1.14+) 安装器使用 SPEC 1 格式,`install_profile.json` 不再包含 `versionInfo` 对象。版本数据存储在独立的 `version.json` 文件中,通过 `json` 字段引用。

**修复**: `download.ts` 中的 `downloadForgeInstaller` 和 `downloadNeoforgeInstaller` 现在支持两种格式:
- 旧格式: 直接读取 `versionInfo` 对象
- 新格式: 从 `install_profile.json` 的 `json` 字段获取路径,从 JAR 中提取 `version.json`

### 2. Fabric 版本 ID 不一致 — **已修复**

**根因**: `getVersionForLoader()` 返回 `{mc}-fabric`,但 Fabric API 的版本 JSON 的 `id` 是 `fabric-loader-{ver}-{mc}`。

**修复**: 改为动态发现 — `getVersionForLoader()` 扫描已安装版本目录,通过 `inheritsFrom` 和 `mainClass` 匹配正确的版本 ID,不再硬编码目录名。

### 3. NeoForge 版本 ID 不匹配 — **已修复**

**根因**: `getVersionForLoader()` 构造 `{mc}-neoforge-{nf_ver}`,但实际安装目录名是 `neoforge-{nf_ver}`。

**修复**: 同上,通过动态发现扫描已安装目录,匹配 `mainClass` 包含 `neoforged` 的版本。

### 4. 遗留脚本清理 — **已完成**

- 删除 `scripts/install_forge.py` (功能已被 TS MCP server 替代)
- 删除 `scripts/__pycache__/`
- 更新 `ci_helper.py` 中的 Forge 安装和 MC 启动逻辑,改为调用 TS CLI
- 更新 `justfile` 的 `run`、`dry-run`、`install-forge` 命令使用 TS CLI
- 保留 `launch_mc.py` (仍有 test 脚本依赖,后续可逐步迁移)

## 验证结果

| 版本 | Loader | 安装 | 启动 (dry-run) |
|------|--------|------|----------------|
| 1.21.11 | Forge | ✅ | ✅ |
| 26.1.2 | NeoForge | ✅ (version JSON 正确提取) | ✅ |
| 1.20.6 | Fabric | ✅ (版本发现正常) | ✅ |

## 后续工作

1. 将 `launch_mc.py` 的测试脚本依赖迁移到 TS CLI
2. NeoForge 安装时需要完整的网络连接下载所有依赖库
3. 考虑 Fabric loader 版本从 API 动态获取,而非硬编码
