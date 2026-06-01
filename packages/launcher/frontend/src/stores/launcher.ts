import { defineStore } from 'pinia'
import { ref } from 'vue'

import { listVersions, getMcpPort } from '@/api/versions'
import type { VersionInfo } from '@/types'

export const useLauncherStore = defineStore('launcher', () => {
  const versions = ref<VersionInfo[]>([])
  const mcpPort = ref<number | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchVersions() {
    loading.value = true
    error.value = null
    try {
      versions.value = await listVersions()
    } catch (e) {
      error.value = String(e)
    } finally {
      loading.value = false
    }
  }

  async function fetchMcpPort() {
    try {
      mcpPort.value = await getMcpPort()
    } catch {
      mcpPort.value = null
    }
  }

  return { versions, mcpPort, loading, error, fetchVersions, fetchMcpPort }
})
