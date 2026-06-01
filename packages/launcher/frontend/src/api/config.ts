import type { CommandResult, LauncherConfig } from '@/types'
import { invoke } from '@/api'

export async function getConfig(): Promise<LauncherConfig> {
  const res = await invoke<CommandResult<LauncherConfig>>('get_config')
  if (!res.ok || !res.data) throw new Error(res.error ?? 'failed')
  return res.data
}

export async function saveConfig(config: LauncherConfig): Promise<void> {
  const res = await invoke<CommandResult<null>>('save_config', { config })
  if (!res.ok) throw new Error(res.error ?? 'failed')
}
