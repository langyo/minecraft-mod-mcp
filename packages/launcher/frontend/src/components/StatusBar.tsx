import { defineComponent } from 'vue'

import { useLauncherStore } from '@/stores'

import styles from './StatusBar.module.scss'

export default defineComponent({
  setup() {
    const store = useLauncherStore()

    return () => (
      <div class={styles.statusBar}>
        {store.mcpPort != null ? (
          <span class={styles.connected}>
            MCP Port: {store.mcpPort}
          </span>
        ) : (
          <span class={styles.disconnected}>
            MCP Disconnected
          </span>
        )}
      </div>
    )
  },
})
