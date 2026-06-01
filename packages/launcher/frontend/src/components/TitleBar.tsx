import { defineComponent } from 'vue'
import { getCurrentWindow } from '@tauri-apps/api/window'

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
            <svg width="10" height="1" viewBox="0 0 10 1" fill="currentColor">
              <path d="M0 0.5h10" stroke="currentColor" stroke-width="1" />
            </svg>
          </button>
          <button
            class={[styles.titlebarBtn, styles.maximize].join(' ')}
            onClick={toggleMaximize}
            title="Maximize"
          >
            <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
              <rect x="0.5" y="0.5" width="9" height="9" rx="1" stroke="currentColor" stroke-width="1" />
            </svg>
          </button>
          <button
            class={[styles.titlebarBtn, styles.close].join(' ')}
            onClick={close}
            title="Close"
          >
            <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
              <path d="M1 1l8 8m0-8l-8 8" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" />
            </svg>
          </button>
        </div>
      </div>
    )
  },
})
