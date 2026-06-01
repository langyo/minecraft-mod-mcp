import { defineComponent, onMounted } from 'vue'

import { useLauncherStore } from '@/stores'
import VersionItem from '@/components/VersionItem'
import type { VersionInfo } from '@/types'

import styles from './Sidebar.module.scss'

export default defineComponent({
  emits: ['select'],
  setup(_, { emit }) {
    const store = useLauncherStore()

    onMounted(() => {
      store.fetchVersions()
      store.fetchMcpPort()
    })

    function handleSelect(version: VersionInfo) {
      emit('select', version)
    }

    return () => (
      <div class={styles.sidebarContent}>
        <div class={styles.searchBar}>
          <input
            class={styles.searchInput}
            type="text"
            placeholder="Search versions..."
          />
        </div>
        <div class={styles.versionList}>
          {store.loading ? (
            <div class={styles.loading}>Loading...</div>
          ) : store.error ? (
            <div class={styles.error}>{store.error}</div>
          ) : (
            store.versions.map((v) => (
              <VersionItem
                key={v.mc_version}
                version={v}
                onSelect={() => handleSelect(v)}
              />
            ))
          )}
        </div>
        <div class={styles.sidebarStatus}>
          <span class={[styles.statusDot, store.mcpPort != null && styles.connected].join(' ')}>
            {store.mcpPort != null ? `MCP :${store.mcpPort}` : 'MCP Offline'}
          </span>
        </div>
      </div>
    )
  },
})
