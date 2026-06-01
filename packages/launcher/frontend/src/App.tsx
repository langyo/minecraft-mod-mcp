import { defineComponent, ref, onMounted, onUnmounted } from 'vue'
import { Transition } from 'vue'
import { RouterView } from 'vue-router'

import Sidebar from '@/components/Sidebar'

import styles from './App.module.scss'

export default defineComponent({
  setup() {
    const sidebarCollapsed = ref(false)
    const rightPanelOpen = ref(false)
    const isMobile = ref(false)

    function checkMobile() {
      isMobile.value = window.innerWidth < 768
      if (isMobile.value) {
        sidebarCollapsed.value = true
        rightPanelOpen.value = false
      }
    }

    onMounted(() => {
      checkMobile()
      window.addEventListener('resize', checkMobile)
    })

    onUnmounted(() => {
      window.removeEventListener('resize', checkMobile)
    })

    return () => (
      <div
        class={[
          styles.shell,
          sidebarCollapsed.value && styles.sidebarCollapsed,
          !rightPanelOpen.value && styles.rightCollapsed,
          isMobile.value && styles.mobile,
        ].filter(Boolean).join(' ')}
      >
        <aside
          class={[
            styles.sidebar,
            sidebarCollapsed.value && styles.collapsed,
          ].filter(Boolean).join(' ')}
        >
          <div class={styles.sidebarHeader}>
            <span class={styles.logo}>&#x26CF;</span>
            {!sidebarCollapsed.value && (
              <span class={styles.appName}>MCP Launcher</span>
            )}
          </div>
          {!sidebarCollapsed.value && <Sidebar />}
          <div class={styles.sidebarFooter}>
            <button
              class={styles.iconBtn}
              onClick={() => {
                sidebarCollapsed.value = !sidebarCollapsed.value
              }}
              title={sidebarCollapsed.value ? 'Expand' : 'Collapse'}
            >
              {sidebarCollapsed.value ? <span>&#9776;</span> : <span>&#9664;</span>}
            </button>
          </div>
        </aside>

        <main class={styles.center}>
          <header class={styles.centerHeader}>
            {sidebarCollapsed.value && (
              <button
                class={styles.iconBtn}
                onClick={() => {
                  sidebarCollapsed.value = false
                }}
              >
                &#9776;
              </button>
            )}
            <span class={styles.headerLogo}>&#x26CF;</span>
            <span class={styles.headerTitle}>Minecraft MCP Launcher</span>
            <div class={styles.headerActions}>
              <button
                class={styles.iconBtn}
                onClick={() => {
                  rightPanelOpen.value = !rightPanelOpen.value
                }}
                title="Detail panel"
              >
                &#x2630;
              </button>
            </div>
          </header>
          <div class={styles.centerBody}>
            <RouterView />
          </div>
        </main>

        <Transition
          enterActiveClass={styles.slideRightEnterActive}
          leaveActiveClass={styles.slideRightLeaveActive}
          enterFromClass={styles.slideRightEnterFrom}
          leaveToClass={styles.slideRightLeaveTo}
        >
          {rightPanelOpen.value && (
            <aside class={styles.rightPanel}>
              <div class={styles.panelContent}>
                <p class={styles.panelPlaceholder}>Select a version to view details</p>
              </div>
            </aside>
          )}
        </Transition>
      </div>
    )
  },
})
