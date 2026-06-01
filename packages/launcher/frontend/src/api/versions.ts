import { invoke } from '@/api'
import type { CommandResult, VersionInfo } from '@/types'

export async function listVersions(): Promise<VersionInfo[]> {
  const res = await invoke<CommandResult<VersionInfo[]>>('list_versions')
  if (!res.ok || !res.data) throw new Error(res.error ?? 'failed to list versions')
  return res.data
}

export async function getVersion(mc: string): Promise<VersionInfo> {
  const res = await invoke<CommandResult<VersionInfo>>('get_version', { mc })
  if (!res.ok || !res.data) throw new Error(res.error ?? 'failed to get version')
  return res.data
}

export async function getMcpPort(): Promise<number | null> {
  const res = await invoke<CommandResult<number | null>>('get_mcp_port')
  if (!res.ok) throw new Error(res.error ?? 'failed to get mcp port')
  return res.data
}

export async function setMcpPort(port: number): Promise<void> {
  const res = await invoke<CommandResult<void>>('set_mcp_port', { port })
  if (!res.ok) throw new Error(res.error ?? 'failed to set mcp port')
}
