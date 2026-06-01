import { defineComponent, onMounted } from 'vue'

import { useLauncherStore } from '@/stores'
import VersionList from '@/components/VersionList'
import StatusBar from '@/components/StatusBar'

import styles from './HomeView.module.scss'

export default defineComponent({
  setup() {
    const store = useLauncherStore()

    onMounted(() => {
      store.fetchVersions()
      store.fetchMcpPort()
    })

    return () => (
      <div class={styles.homeView}>
        <header class={styles.header}>
          <h1 class={styles.title}>Minecraft MCP Launcher</h1>
          <StatusBar />
        </header>
        <main class={styles.content}>
          {store.loading ? (
            <div class={styles.loading}>Loading versions...</div>
          ) : store.error ? (
            <div class={styles.error}>{store.error}</div>
          ) : (
            <VersionList versions={store.versions} />
          )}
        </main>
      </div>
    )
  },
})
