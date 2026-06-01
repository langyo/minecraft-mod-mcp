import { defineComponent, onMounted } from 'vue'

import { useLauncherStore } from '@/stores'
import VersionItem from '@/components/VersionItem'

import styles from './Sidebar.module.scss'

export default defineComponent({
  setup() {
    const store = useLauncherStore()

    onMounted(() => {
      store.fetchVersions()
    })

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
              <VersionItem key={v.mc_version} version={v} />
            ))
          )}
        </div>
      </div>
    )
  },
})
