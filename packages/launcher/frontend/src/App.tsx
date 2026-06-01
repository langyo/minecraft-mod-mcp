import { defineComponent, ref, onMounted, onUnmounted, watch, type PropType } from 'vue'
import { Transition } from 'vue'
import { RouterView } from 'vue-router'

import { X } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'

import type { VersionInfo } from '@/types'
import styles from './App.module.scss'
import Sidebar from '@/components/Sidebar'
import TitleBar from '@/components/TitleBar'
import { useLauncherStore } from '@/stores'
import { getLoaders } from '@/types'

export default defineComponent({
  setup() {
    const { t, locale } = useI18n()
    const store = useLauncherStore()
    const rightPanelOpen = ref(false)
    const selectedVersion = ref<VersionInfo | null>(null)

    function handleSelectVersion(v: VersionInfo) {
      if (selectedVersion.value?.mc_version === v.mc_version && rightPanelOpen.value) {
        rightPanelOpen.value = false
        store.setSelectedVersion(null)
        return
      }
      selectedVersion.value = v
      store.setSelectedVersion(v)
      rightPanelOpen.value = true
    }

    onMounted(() => {
      window.addEventListener('resize', () => {})
      store.fetchVersions()
      store.fetchMcpPort()
      store.fetchConfig()
      store.fetchProcesses()
    })

    watch(() => store.config?.language, (lang) => {
      if (lang) locale.value = lang
    })

    watch(() => store.config?.theme, (theme) => {
      if (theme === 'light') {
        document.documentElement.classList.add('light')
      } else {
        document.documentElement.classList.remove('light')
      }
    })

    onMounted(() => {
      const theme = store.config?.theme
      if (theme === 'light') {
        document.documentElement.classList.add('light')
      }
    })

    onUnmounted(() => {
      window.removeEventListener('resize', () => {})
    })

        return () => (
      <div class={styles.root}>
        {!store.config && <div class={styles.initLoading}>{t('common.loading')}</div>}
        <TitleBar />
        <div class={styles.shell}>
          <aside class={styles.sidebar}>
            <Sidebar onSelect={handleSelectVersion} />
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
                  <DetailPanel version={selectedVersion.value} onClose={() => { rightPanelOpen.value = false; store.setSelectedVersion(null) }} />
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
    onClose: { type: Function as PropType<() => void>, default: undefined },
  },
  setup(props) {
    const { t } = useI18n()
    return () => (
      <div class={styles.detailPanel}>
        <div class={styles.detailHeader}>
          <span class={styles.detailMcVer}>{props.version.mc_version}</span>
          <span class={[styles.detailBadge, styles.badgeJava].join(' ')}>{t('common.jdk')} {props.version.java}</span>
          {props.onClose && (
            <button class={styles.iconBtn} onClick={props.onClose} style={{ marginLeft: 'auto' }}>
              <X size={14} />
            </button>
          )}
        </div>

        <div class={styles.detailSection}>
          <h3 class={styles.sectionTitle}>{t('detail.forge')}</h3>
          <code class={styles.codeBlock}>{props.version.forge}</code>
        </div>

        {props.version.neoforge && (
          <div class={styles.detailSection}>
            <h3 class={styles.sectionTitle}>{t('detail.neoForge')}</h3>
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
            <span class={styles.statValue}>{['fg21', 'fg22', 'fg23', 'fg3', 'fg41'].includes(props.version.fg_era) ? t('detail.yes') : t('detail.no')}</span>
            <span class={styles.statLabel}>{t('detail.legacy')}</span>
          </div>
        </div>
      </div>
    )
  },
})
