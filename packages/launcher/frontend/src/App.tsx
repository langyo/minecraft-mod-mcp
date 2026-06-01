import { defineComponent } from 'vue'
import { RouterView } from 'vue-router'

import styles from './App.module.scss'

export default defineComponent({
  setup() {
    return () => (
      <div class={styles.launcherApp}>
        <RouterView />
      </div>
    )
  },
})
