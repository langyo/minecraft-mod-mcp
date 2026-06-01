import { defineComponent } from 'vue'
import { getCurrentWindow } from '@tauri-apps/api/window'
import { Minus, Square, X } from 'lucide-vue-next'

import styles from './TitleBar.module.scss'

export default defineComponent({
  setup() {
    const appWindow = getCurrentWindow()

    function handleDrag(e: MouseEvent) {
      if ((e.target as HTMLElement).closest('button')) return
      appWindow.startDragging()
    }

    function handleDoubleClick() {
      appWindow.toggleMaximize()
    }

    function minimize() {
      appWindow.minimize()
    }

    function toggleMaximize() {
      appWindow.toggleMaximize()
    }

    function close() {
      appWindow.close()
    }

    return () => (
      <div
        class={styles.titlebar}
        data-tauri-drag-region
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
            title="Minimize"
          >
            <Minus size={10} />
          </button>
          <button
            class={[styles.titlebarBtn, styles.maximize].join(' ')}
            onClick={toggleMaximize}
            title="Maximize"
          >
            <Square size={10} />
          </button>
          <button
            class={[styles.titlebarBtn, styles.close].join(' ')}
            onClick={close}
            title="Close"
          >
            <X size={12} />
          </button>
        </div>
      </div>
    )
  },
})
