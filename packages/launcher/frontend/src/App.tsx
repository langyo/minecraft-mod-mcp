import { defineComponent, ref, onMounted, watch } from 'vue'
import { RouterView } from 'vue-router'

import { X } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'

import styles from './App.module.scss'
import Sidebar from '@/components/Sidebar'
import TitleBar from '@/components/TitleBar'
import { useLauncherStore } from '@/stores'

function applyTheme(isDark: boolean) {
  document.documentElement.classList.toggle('light', !isDark)
}

export default defineComponent({
  setup() {
    const { t, locale } = useI18n()
    const store = useLauncherStore()

    onMounted(() => {
      store.fetchVersions()
      store.fetchConfig()
      store.fetchProcesses()
    })

    watch(() => store.config?.language, (lang) => {
      if (lang) locale.value = lang
    })

    watch(() => store.config, (cfg) => {
      const follow = cfg?.follow_system_theme !== false
      if (follow) {
        applyTheme(window.matchMedia('(prefers-color-scheme: dark)').matches)
      } else {
        applyTheme(cfg?.theme !== 'light')
      }
    }, { immediate: true })

    return () => (
      <div class={styles.root}>
        {!store.config && <div class={styles.initLoading}>{t('common.loading')}</div>}
        <TitleBar />
        <div class={styles.shell}>
          <aside class={styles.sidebar}>
            <Sidebar />
          </aside>

          <main class={styles.center}>
            <div class={styles.centerBody}>
              <RouterView />
            </div>
          </main>
        </div>
      </div>
    )
  },
})
