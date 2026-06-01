import type { CommandResult, DeviceCodeInfo, MicrosoftProfile, JavaInfo } from '@/types'
import { invoke } from '@/api'

export async function startMicrosoftAuth(): Promise<DeviceCodeInfo> {
  const res = await invoke<CommandResult<DeviceCodeInfo>>('start_microsoft_auth')
  if (!res.ok || !res.data) throw new Error(res.error ?? 'failed')
  return res.data
}

export async function pollMicrosoftAuth(deviceCode: string): Promise<MicrosoftProfile> {
  const res = await invoke<CommandResult<MicrosoftProfile>>('poll_microsoft_auth', { device_code: deviceCode })
  if (!res.ok || !res.data) throw new Error(res.error ?? 'failed')
  return res.data
}

export async function addOfflineAccount(username: string, uuid?: string): Promise<void> {
  const args: Record<string, string> = { username }
  if (uuid) args.uuid = uuid
  const res = await invoke<CommandResult<null>>('add_offline_account', args)
  if (!res.ok) throw new Error(res.error ?? 'failed')
}

export async function removeAccount(uuid: string): Promise<void> {
  const res = await invoke<CommandResult<null>>('remove_account', { uuid })
  if (!res.ok) throw new Error(res.error ?? 'failed')
}

export async function selectAccount(uuid: string): Promise<void> {
  const res = await invoke<CommandResult<null>>('select_account', { uuid })
  if (!res.ok) throw new Error(res.error ?? 'failed')
}

export async function refreshAccount(uuid: string): Promise<void> {
  const res = await invoke<CommandResult<null>>('refresh_account', { uuid })
  if (!res.ok) throw new Error(res.error ?? 'failed')
}

export async function detectJavas(): Promise<JavaInfo[]> {
  const res = await invoke<CommandResult<JavaInfo[]>>('detect_javas')
  if (!res.ok || !res.data) throw new Error(res.error ?? 'failed')
  return res.data
}
