import { defineComponent, ref, onMounted, onUnmounted, type PropType } from 'vue'
import { Transition } from 'vue'
import { RouterView } from 'vue-router'

import TitleBar from '@/components/TitleBar'
import Sidebar from '@/components/Sidebar'
import type { VersionInfo } from '@/types'
import { getLoaders } from '@/types'

import styles from './App.module.scss'

export default defineComponent({
  setup() {
    const sidebarCollapsed = ref(false)
    const rightPanelOpen = ref(true)
    const isMobile = ref(false)
    const selectedVersion = ref<VersionInfo | null>(null)

    function checkMobile() {
      isMobile.value = window.innerWidth < 768
      if (isMobile.value) {
        sidebarCollapsed.value = true
        rightPanelOpen.value = false
      }
    }

    function handleSelectVersion(v: VersionInfo) {
      if (selectedVersion.value?.mc_version === v.mc_version && rightPanelOpen.value) {
        return
      }
      selectedVersion.value = v
      if (!rightPanelOpen.value) {
        rightPanelOpen.value = true
      }
      if (isMobile.value) {
        sidebarCollapsed.value = true
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
      <div class={styles.root}>
        <TitleBar />
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
              <img class={styles.logoImg} src="/logo.webp" alt="MMML" />
              {!sidebarCollapsed.value && (
                <span class={styles.appName}>MMML</span>
              )}
            </div>
            {!sidebarCollapsed.value && (
              <Sidebar onSelect={handleSelectVersion} />
            )}
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
                {selectedVersion.value ? (
                  <DetailPanel version={selectedVersion.value} />
                ) : (
                  <div class={styles.panelContent}>
                    <p class={styles.panelPlaceholder}>Select a version to view details</p>
                  </div>
                )}
              </aside>
            )}
          </Transition>
        </div>
      </div>
    )
  },
})

const DetailPanel = defineComponent({
  props: {
    version: { type: Object as PropType<VersionInfo>, required: true },
  },
  setup(props) {
    const s = useDetailStyles()

    return () => (
      <div class={s.detail}>
        <div class={s.detailHeader}>
          <span class={s.detailMcVer}>{props.version.mc_version}</span>
          <span class={[s.detailBadge, s.java].join(' ')}>JDK {props.version.java}</span>
        </div>

        <div class={s.detailSection}>
          <h3 class={s.sectionTitle}>Forge</h3>
          <code class={s.codeBlock}>{props.version.forge}</code>
        </div>

        {props.version.neoforge && (
          <div class={s.detailSection}>
            <h3 class={s.sectionTitle}>NeoForge</h3>
            <code class={s.codeBlock}>{props.version.neoforge}</code>
          </div>
        )}

        <div class={s.detailSection}>
          <h3 class={s.sectionTitle}>Version ID</h3>
          <code class={s.codeBlock}>{props.version.version_id}</code>
        </div>

        <div class={s.detailSection}>
          <h3 class={s.sectionTitle}>FG Era</h3>
          <code class={s.codeBlock}>{props.version.fg_era}</code>
        </div>

        <div class={s.detailSection}>
          <h3 class={s.sectionTitle}>Mappings</h3>
          <code class={s.codeBlock}>{props.version.mappings}</code>
        </div>

        <div class={s.detailRow}>
          <div class={s.detailStat}>
            <span class={s.statValue}>{getLoaders(props.version).length}</span>
            <span class={s.statLabel}>Loaders</span>
          </div>
          <div class={s.detailStat}>
            <span class={s.statValue}>--</span>
            <span class={s.statLabel}>Legacy</span>
          </div>
        </div>
      </div>
    )
  },
})

function useDetailStyles() {
  return {
    detail: 'detail-panel',
    detailHeader: 'detail-header',
    detailMcVer: 'detail-mc-ver',
    detailBadge: 'detail-badge',
    java: 'badge-java',
    detailSection: 'detail-section',
    sectionTitle: 'section-title',
    codeBlock: 'code-block',
    detailRow: 'detail-row',
    detailStat: 'detail-stat',
    statValue: 'stat-value',
    statLabel: 'stat-label',
  }
}
