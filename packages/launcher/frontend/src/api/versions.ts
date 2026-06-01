import { invoke } from '@/api'
import type { CommandResult, VersionInfo, ManifestVersion } from '@/types'

export async function listVersions(): Promise<VersionInfo[]> {
  const res = await invoke<CommandResult<VersionInfo[]>>('list_versions')
  if (!res.ok || !res.data) throw new Error(res.error ?? 'failed')
  return res.data
}

export async function getVersion(mc: string): Promise<VersionInfo> {
  const res = await invoke<CommandResult<VersionInfo>>('get_version', { mc })
  if (!res.ok || !res.data) throw new Error(res.error ?? 'failed')
  return res.data
}

export async function getMcpPort(): Promise<number | null> {
  const res = await invoke<CommandResult<number | null>>('get_mcp_port')
  if (!res.ok) throw new Error(res.error ?? 'failed')
  return res.data
}

export async function setMcpPort(port: number): Promise<void> {
  const res = await invoke<CommandResult<null>>('set_mcp_port', { port })
  if (!res.ok) throw new Error(res.error ?? 'failed')
}

export async function fetchRemoteVersions(): Promise<ManifestVersion[]> {
  const res = await invoke<CommandResult<{ latest: { release: string; snapshot: string }; versions: ManifestVersion[] }>>('fetch_remote_versions')
  if (!res.ok || !res.data) throw new Error(res.error ?? 'failed')
  return res.data.versions
}

export async function installVersion(versionId: string, versionUrl: string): Promise<void> {
  const res = await invoke<CommandResult<null>>('install_version', { versionId, versionUrl })
  if (!res.ok) throw new Error(res.error ?? 'failed')
}

export async function listInstalledVersions(): Promise<string[]> {
  const res = await invoke<CommandResult<string[]>>('list_installed_versions')
  if (!res.ok || !res.data) throw new Error(res.error ?? 'failed')
  return res.data
}

export async function launchGame(versionId: string, loader?: string): Promise<void> {
  const args: Record<string, string> = { versionId }
  if (loader) args.loader = loader
  const res = await invoke<CommandResult<null>>('launch_game', args)
  if (!res.ok) throw new Error(res.error ?? 'failed')
}
