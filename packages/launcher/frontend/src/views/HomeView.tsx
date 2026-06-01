import { defineComponent } from 'vue'

import { useLauncherStore } from '@/stores'
import { getLoaders } from '@/types'

import styles from './HomeView.module.scss'

export default defineComponent({
  setup() {
    const store = useLauncherStore()

    return () => (
      <div class={styles.home}>
        <div class={styles.welcome}>
          <img class={styles.welcomeIcon} src="/logo.webp" alt="MMML" />
          <h2>Minecraft Mod MCP Launcher</h2>
          <p class={styles.subtitle}>MMML — Select a version from the sidebar</p>
          <div class={styles.stats}>
            <div class={styles.stat}>
              <span class={styles.statValue}>{store.versions.length}</span>
              <span class={styles.statLabel}>Versions</span>
            </div>
            <div class={styles.stat}>
              <span class={styles.statValue}>
                {store.versions.reduce((acc, v) => acc + getLoaders(v).length, 0)}
              </span>
              <span class={styles.statLabel}>Profiles</span>
            </div>
            <div class={styles.stat}>
              <span class={[styles.statValue, store.mcpPort != null && styles.connected].filter(Boolean).join(' ')}>
                {store.mcpPort ?? '--'}
              </span>
              <span class={styles.statLabel}>MCP Port</span>
            </div>
          </div>
        </div>
      </div>
    )
  },
})
