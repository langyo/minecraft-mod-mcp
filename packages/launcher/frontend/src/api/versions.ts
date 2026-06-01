import type { CommandResult, VersionInfo, ManifestVersion, RunningProcess } from '@/types'
import { invoke } from '@/api'

export async function listVersions(): Promise<VersionInfo[]> {
  const res = await invoke<CommandResult<VersionInfo[]>>('list_versions')
  if (!res.ok || !res.data) throw new Error(res.error ?? 'failed')
  return res.data
}

export async function getMcpPort(): Promise<number | null> {
  const res = await invoke<CommandResult<number | null>>('get_mcp_port')
  if (!res.ok) throw new Error(res.error ?? 'failed')
  return res.data
}

export async function fetchRemoteVersions(): Promise<ManifestVersion[]> {
  const res = await invoke<CommandResult<{ latest: { release: string; snapshot: string }; versions: ManifestVersion[] }>>('fetch_remote_versions')
  if (!res.ok || !res.data) throw new Error(res.error ?? 'failed')
  return res.data.versions
}

export async function installVersion(versionId: string, versionUrl: string): Promise<void> {
  const res = await invoke<CommandResult<null>>('install_version', { version_id: versionId, version_url: versionUrl })
  if (!res.ok) throw new Error(res.error ?? 'failed')
}

export async function listInstalledVersions(): Promise<string[]> {
  const res = await invoke<CommandResult<string[]>>('list_installed_versions')
  if (!res.ok || !res.data) throw new Error(res.error ?? 'failed')
  return res.data
}

export async function launchGame(versionId: string, loader?: string): Promise<number> {
  const args: Record<string, string> = { version_id: versionId }
  if (loader) args.loader = loader
  const res = await invoke<CommandResult<number>>('launch_game', args)
  if (!res.ok || res.data == null) throw new Error(res.error ?? 'failed')
  return res.data
}

export async function listRunningProcesses(): Promise<RunningProcess[]> {
  const res = await invoke<CommandResult<RunningProcess[]>>('list_running_processes')
  if (!res.ok || !res.data) throw new Error(res.error ?? 'failed')
  return res.data
}

export async function killProcess(id: number): Promise<void> {
  const res = await invoke<CommandResult<null>>('kill_process', { id })
  if (!res.ok) throw new Error(res.error ?? 'failed')
}
