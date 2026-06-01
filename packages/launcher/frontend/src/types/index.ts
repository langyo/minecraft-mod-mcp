export interface VersionInfo {
  mc_version: string
  forge: string
  fg_era: string
  java: number
  mappings: string
  version_id: string
  neoforge: string | null
  mdg: string | null
  fabric_yarn: string | null
}

export type Loader = 'forge' | 'neoforge' | 'fabric'

export interface CommandResult<T> {
  ok: boolean
  data: T | null
  error: string | null
}

export interface JavaInfo {
  path: string
  version: number
  vendor: string
  is_jdk: boolean
}

export interface Account {
  type: 'microsoft' | 'offline'
  uuid: string
  username: string
  access_token?: string
  refresh_token?: string
  not_after?: number
}

export type DownloadSource = 'mojang' | 'bmclapi'
export type Language = 'zh-CN' | 'en-US'

export interface LauncherConfig {
  java_dir: string | null
  java_version: number | null
  max_memory_mb: number
  min_memory_mb: number
  game_dir: string | null
  java_args: string | null
  game_args: string | null
  width: number
  height: number
  fullscreen: boolean
  accounts: Account[]
  selected_account: string | null
  download_source: DownloadSource
  mcp_port: number | null
  language: Language
}

export interface ManifestVersion {
  id: string
  type: string
  url: string
  time: string
  releaseTime: string
}

export interface DeviceCodeInfo {
  user_code: string
  device_code: string
  verification_uri: string
  interval: number
  expires_in: number
  message: string
}

export interface MicrosoftProfile {
  uuid: string
  username: string
  access_token: string
  refresh_token: string
  expires_at: number
}

export function getLoaders(v: VersionInfo): Loader[] {
  const loaders: Loader[] = []
  if (v.forge) loaders.push('forge')
  if (v.neoforge) loaders.push('neoforge')
  if (v.fabric_yarn) loaders.push('fabric')
  return loaders
}
