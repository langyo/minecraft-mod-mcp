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
