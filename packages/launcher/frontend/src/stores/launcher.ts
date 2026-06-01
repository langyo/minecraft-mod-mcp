import { defineStore } from 'pinia'
import { ref } from 'vue'

import type { VersionInfo, LauncherConfig, ManifestVersion, RunningProcess } from '@/types'
import { getConfig } from '@/api/config'
import { listVersions, getMcpPort, fetchRemoteVersions, listInstalledVersions, listRunningProcesses } from '@/api/versions'

export const useLauncherStore = defineStore('launcher', () => {
  const versions = ref<VersionInfo[]>([])
  const mcpPort = ref<number | null>(null)
  const config = ref<LauncherConfig | null>(null)
  const remoteVersions = ref<ManifestVersion[]>([])
  const installedVersions = ref<string[]>([])
  const runningProcesses = ref<RunningProcess[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  const selectedVersion = ref<VersionInfo | null>(null)

  function setSelectedVersion(v: VersionInfo | null) {
    selectedVersion.value = v
  }

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

  async function fetchConfig() {
    try {
      config.value = await getConfig()
    } catch (e) {
      error.value = String(e)
    }
  }

  async function fetchRemote() {
    loading.value = true
    error.value = null
    try {
      remoteVersions.value = await fetchRemoteVersions()
    } catch (e) {
      error.value = String(e)
    } finally {
      loading.value = false
    }
  }

  async function fetchInstalled() {
    try {
      installedVersions.value = await listInstalledVersions()
    } catch {
      installedVersions.value = []
    }
  }

  async function fetchProcesses() {
    try {
      runningProcesses.value = await listRunningProcesses()
    } catch {
      runningProcesses.value = []
    }
  }

  return {
    versions,
    mcpPort,
    config,
    remoteVersions,
    installedVersions,
    runningProcesses,
    loading,
    error,
    selectedVersion,
    setSelectedVersion,
    fetchVersions,
    fetchMcpPort,
    fetchConfig,
    fetchRemote,
    fetchInstalled,
    fetchProcesses,
  }
})
