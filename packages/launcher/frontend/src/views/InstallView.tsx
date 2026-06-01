import { defineComponent, ref, computed, onMounted } from 'vue'

import { useLauncherStore } from '@/stores'
import { fetchRemoteVersions, installVersion, listInstalledVersions } from '@/api/versions'
import type { ManifestVersion } from '@/types'

import styles from './InstallView.module.scss'

type FilterType = 'all' | 'release' | 'snapshot'

export default defineComponent({
  setup() {
    const store = useLauncherStore()
    const filter = ref<FilterType>('all')
    const search = ref('')
    const installing = ref<string | null>(null)
    const installError = ref<string | null>(null)

    const versions = computed(() => {
      let list = store.remoteVersions
      if (filter.value === 'release') {
        list = list.filter((v) => v.type === 'release')
      } else if (filter.value === 'snapshot') {
        list = list.filter((v) => v.type === 'snapshot')
      }
      if (search.value.trim()) {
        const q = search.value.trim().toLowerCase()
        list = list.filter((v) => v.id.toLowerCase().includes(q))
      }
      return list
    })

    const installedSet = computed(() => new Set(store.installedVersions))

    async function handleRefresh() {
      try {
        await store.fetchRemote()
        await store.fetchInstalled()
      } catch (e) {
        console.error(e)
      }
    }

    async function handleInstall(v: ManifestVersion) {
      installing.value = v.id
      installError.value = null
      try {
        await installVersion(v.id, v.url)
        await store.fetchInstalled()
      } catch (e) {
        installError.value = String(e)
      } finally {
        installing.value = null
      }
    }

    function formatDate(dateStr: string) {
      try {
        return new Date(dateStr).toLocaleDateString()
      } catch {
        return dateStr.slice(0, 10)
      }
    }

    onMounted(() => {
      if (store.remoteVersions.length === 0) {
        handleRefresh()
      } else {
        store.fetchInstalled()
      }
    })

    return () => (
      <div class={styles.install}>
        <h1 class={styles.pageTitle}>Install Version</h1>
        <p class={styles.pageSubtitle}>Browse and install Minecraft versions</p>

        <div class={styles.toolbar}>
          <input
            class={styles.searchInput}
            type="text"
            placeholder="Search versions..."
            value={search.value}
            onInput={(e) => { search.value = (e.target as HTMLInputElement).value }}
          />
          <div class={styles.filterGroup}>
            {(['all', 'release', 'snapshot'] as FilterType[]).map((f) => (
              <button
                key={f}
                class={[styles.filterBtn, filter.value === f && styles.filterBtnActive].filter(Boolean).join(' ')}
                onClick={() => { filter.value = f }}
              >
                {f.charAt(0).toUpperCase() + f.slice(1)}
              </button>
            ))}
          </div>
          <button class={styles.btn} onClick={handleRefresh} disabled={store.loading}>
            Refresh
          </button>
        </div>

        {installError.value && <p class={styles.error}>{installError.value}</p>}

        <div class={styles.versionTable}>
          <div class={[styles.versionRow, styles.versionRowHeader].join(' ')}>
            <span>Version</span>
            <span style={{ textAlign: 'center' }}>Type</span>
            <span style={{ textAlign: 'center' }}>Released</span>
            <span style={{ textAlign: 'right' }}>Action</span>
          </div>

          {store.loading ? (
            <div class={styles.loading}>Loading versions...</div>
          ) : versions.value.length === 0 ? (
            <div class={styles.empty}>No versions found</div>
          ) : (
            versions.value.map((v) => (
              <div key={v.id} class={styles.versionRow}>
                <span class={styles.colId}>{v.id}</span>
                <span class={styles.colType}>
                  <span
                    class={[
                      styles.typeBadge,
                      v.type === 'release' ? styles.typeRelease
                        : v.type === 'snapshot' ? styles.typeSnapshot
                        : styles.typeOther,
                    ].join(' ')}
                  >
                    {v.type}
                  </span>
                </span>
                <span class={styles.colDate}>{formatDate(v.releaseTime)}</span>
                <span class={styles.colAction}>
                  {installedSet.value.has(v.id) ? (
                    <span class={styles.installedBadge}>Installed</span>
                  ) : (
                    <button
                      class={styles.btnInstall}
                      disabled={installing.value === v.id}
                      onClick={() => handleInstall(v)}
                    >
                      {installing.value === v.id ? 'Installing...' : 'Install'}
                    </button>
                  )}
                </span>
              </div>
            ))
          )}
        </div>
      </div>
    )
  },
})
