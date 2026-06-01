import { defineComponent } from 'vue'
import { getCurrentWindow } from '@tauri-apps/api/window'
import { Minus, Square, X } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'

import styles from './TitleBar.module.scss'

export default defineComponent({
  setup() {
    const { t } = useI18n()
    const appWindow = getCurrentWindow()

    function handleDoubleClick() {
      try { appWindow.toggleMaximize() } catch {}
    }

    function minimize() {
      try { appWindow.minimize() } catch {}
    }

    function toggleMaximize() {
      try { appWindow.toggleMaximize() } catch {}
    }

    function close() {
      try { appWindow.close() } catch {}
    }

    return () => (
      <div
        class={styles.titlebar}
        onDblclick={handleDoubleClick}
      >
        <div class={styles.titlebarLeft}>
          <img class={styles.titlebarLogoImg} src="/logo.webp" alt="MMML" />
          <span class={styles.titlebarTitle}>MMML</span>
        </div>
        <div class={styles.titlebarControls}>
          <button
            class={[styles.titlebarBtn, styles.minimize].join(' ')}
            onClick={minimize}
            title={t('titlebar.minimize')}
          >
            <Minus size={10} />
          </button>
          <button
            class={[styles.titlebarBtn, styles.maximize].join(' ')}
            onClick={toggleMaximize}
            title={t('titlebar.maximize')}
          >
            <Square size={10} />
          </button>
          <button
            class={[styles.titlebarBtn, styles.close].join(' ')}
            onClick={close}
            title={t('titlebar.close')}
          >
            <X size={12} />
          </button>
        </div>
      </div>
    )
  },
})
