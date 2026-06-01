import { defineComponent, ref, onMounted, onUnmounted, watch, type PropType } from 'vue'
import { Transition } from 'vue'
import { RouterView } from 'vue-router'
import { useI18n } from 'vue-i18n'

import TitleBar from '@/components/TitleBar'
import Sidebar from '@/components/Sidebar'
import { useLauncherStore } from '@/stores'
import type { VersionInfo } from '@/types'
import { getLoaders } from '@/types'

import styles from './App.module.scss'

export default defineComponent({
  setup() {
    const { t, locale } = useI18n()
    const store = useLauncherStore()
    const sidebarCollapsed = ref(false)
    const rightPanelOpen = ref(false)
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
      store.fetchVersions()
      store.fetchMcpPort()
      store.fetchConfig()
    })

    watch(() => store.config?.language, (lang) => {
      if (lang) locale.value = lang
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
                title={sidebarCollapsed.value ? t('sidebar.expand') : t('sidebar.collapse')}
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
                    <p class={styles.panelPlaceholder}>{t('detail.selectVersion')}</p>
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
    const { t } = useI18n()
    return () => (
      <div class={styles.detailPanel}>
        <div class={styles.detailHeader}>
          <span class={styles.detailMcVer}>{props.version.mc_version}</span>
          <span class={[styles.detailBadge, styles.badgeJava].join(' ')}>JDK {props.version.java}</span>
        </div>

        <div class={styles.detailSection}>
          <h3 class={styles.sectionTitle}>Forge</h3>
          <code class={styles.codeBlock}>{props.version.forge}</code>
        </div>

        {props.version.neoforge && (
          <div class={styles.detailSection}>
            <h3 class={styles.sectionTitle}>NeoForge</h3>
            <code class={styles.codeBlock}>{props.version.neoforge}</code>
          </div>
        )}

        <div class={styles.detailSection}>
          <h3 class={styles.sectionTitle}>{t('detail.versionId')}</h3>
          <code class={styles.codeBlock}>{props.version.version_id}</code>
        </div>

        <div class={styles.detailSection}>
          <h3 class={styles.sectionTitle}>{t('detail.fgEra')}</h3>
          <code class={styles.codeBlock}>{props.version.fg_era}</code>
        </div>

        <div class={styles.detailSection}>
          <h3 class={styles.sectionTitle}>{t('detail.mappings')}</h3>
          <code class={styles.codeBlock}>{props.version.mappings}</code>
        </div>

        <div class={styles.detailRow}>
          <div class={styles.detailStat}>
            <span class={styles.statValue}>{getLoaders(props.version).length}</span>
            <span class={styles.statLabel}>{t('detail.loaders')}</span>
          </div>
          <div class={styles.detailStat}>
            <span class={styles.statValue}>--</span>
            <span class={styles.statLabel}>{t('detail.legacy')}</span>
          </div>
        </div>
      </div>
    )
  },
})
