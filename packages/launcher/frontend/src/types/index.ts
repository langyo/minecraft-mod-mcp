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

export function getLoaders(v: VersionInfo): Loader[] {
  const loaders: Loader[] = []
  if (v.forge) loaders.push('forge')
  if (v.neoforge) loaders.push('neoforge')
  if (v.fabric_yarn) loaders.push('fabric')
  return loaders
}
