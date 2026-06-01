import { defineComponent, ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'

import { Play, Square, RefreshCw, Clock, Cpu, Globe } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'

import type { RunningProcess } from '@/types'
import styles from './HomeView.module.scss'
import { launchGame } from '@/api/versions'
import { killProcess } from '@/api/versions'
import { useLauncherStore } from '@/stores'
import { getLoaders } from '@/types'

function getDefaultLoader(v: import('@/types').VersionInfo): string | undefined {
  const loaders = getLoaders(v)
  return loaders[0]
}

function formatUptime(startedAt: number): string {
  const elapsed = Math.floor(Date.now() / 1000 - startedAt)
  if (elapsed < 60) return `${elapsed}s`
  if (elapsed < 3600) return `${Math.floor(elapsed / 60)}m ${elapsed % 60}s`
  const h = Math.floor(elapsed / 3600)
  const m = Math.floor((elapsed % 3600) / 60)
  return `${h}h ${m}m`
}

export default defineComponent({
  setup() {
    const { t } = useI18n()
    const store = useLauncherStore()
    const router = useRouter()
    const launching = ref(false)
    const launchError = ref<string | null>(null)
    let pollTimer: ReturnType<typeof setInterval> | null = null

    const selectedAccount = computed(() => {
      const cfg = store.config
      if (!cfg || !cfg.selected_account) return null
      return cfg.accounts.find((a) => a.uuid === cfg.selected_account) ?? null
    })

    onMounted(() => {
      pollTimer = setInterval(() => {
        store.fetchProcesses()
      }, 3000)
    })

    onUnmounted(() => {
      if (pollTimer) clearInterval(pollTimer)
    })

    async function handleLaunch() {
      if (!store.selectedVersion) {
        launchError.value = t('home.selectVersion')
        return
      }
      if (!store.config?.selected_account) {
        launchError.value = t('home.noAccount')
        return
      }
      launching.value = true
      launchError.value = null
      try {
        await launchGame(store.selectedVersion.version_id, getDefaultLoader(store.selectedVersion))
        await store.fetchProcesses()
      } catch (e) {
        launchError.value = String(e)
      } finally {
        launching.value = false
      }
    }

    async function handleKill(proc: RunningProcess) {
      try {
        await killProcess(proc.id)
        await store.fetchProcesses()
      } catch (e) {
        launchError.value = String(e)
      }
    }

    return () => (
      <div class={styles.home}>
        <div class={styles.header}>
          <img class={styles.logo} src="/logo.webp" alt="MMML" />
          <div>
            <h1 class={styles.pageTitle}>{t('dashboard.title')}</h1>
            <p class={styles.pageSubtitle}>{t('dashboard.subtitle')}</p>
          </div>
        </div>

        <div class={styles.summaryCards}>
          <div class={styles.summaryCard}>
            <div class={styles.summaryIcon}><Play size={20} /></div>
            <div class={styles.summaryInfo}>
              <span class={styles.summaryValue}>{store.runningProcesses.length}</span>
              <span class={styles.summaryLabel}>{t('dashboard.running')}</span>
            </div>
          </div>
          <div class={styles.summaryCard}>
            <div class={styles.summaryIcon}><Cpu size={20} /></div>
            <div class={styles.summaryInfo}>
              <span class={[styles.summaryValue, store.mcpPort != null && styles.connected].filter(Boolean).join(' ')}>
                {store.mcpPort ?? '--'}
              </span>
              <span class={styles.summaryLabel}>{t('dashboard.mcpPort')}</span>
            </div>
          </div>
          <div class={styles.summaryCard}>
            <div class={styles.summaryIcon}><Globe size={20} /></div>
            <div class={styles.summaryInfo}>
              <span class={styles.summaryValue}>
                {store.runningProcesses.filter((p) => p.mcp_port != null).length}
              </span>
              <span class={styles.summaryLabel}>{t('dashboard.debuggable')}</span>
            </div>
          </div>
        </div>

        <div class={styles.section}>
          <div class={styles.sectionHeader}>
            <h2 class={styles.sectionTitle}>{t('dashboard.instances')}</h2>
            <button
              class={styles.iconBtn}
              onClick={() => store.fetchProcesses()}
              title={t('dashboard.refresh')}
            >
              <RefreshCw size={14} />
            </button>
          </div>

          {store.runningProcesses.length === 0 ? (
            <div class={styles.emptyState}>
              <p>{t('dashboard.noInstances')}</p>
            </div>
          ) : (
            <div class={styles.processList}>
              {store.runningProcesses.map((proc) => (
                <div key={proc.id} class={styles.processCard}>
                  <div class={styles.processMain}>
                    <div class={styles.processInfo}>
                      <span class={styles.processVersion}>{proc.version_id}</span>
                      <span class={styles.processLoader}>{proc.loader}</span>
                    </div>
                    <div class={styles.processMeta}>
                      <span class={styles.processMetaItem}>
                        <Clock size={12} /> {formatUptime(proc.started_at)}
                      </span>
                      <span class={styles.processMetaItem}>PID: {proc.pid}</span>
                      {proc.mcp_port != null && (
                        <span class={[styles.processMetaItem, styles.mcpConnected].join(' ')}>
                          MCP :{proc.mcp_port}
                        </span>
                      )}
                    </div>
                  </div>
                  <div class={styles.processActions}>
                    {proc.mcp_port != null && (
                      <button
                        class={styles.btnConnect}
                        onClick={() => router.push('/mcp')}
                      >
                        {t('dashboard.connect')}
                      </button>
                    )}
                    <button
                      class={styles.btnKill}
                      onClick={() => handleKill(proc)}
                    >
                      <Square size={12} /> {t('dashboard.kill')}
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        <div class={styles.launchSection}>
          {selectedAccount.value ? (
            <div class={styles.accountRow}>
              <span
                class={[
                  styles.accountBadge,
                  selectedAccount.value.type === 'microsoft'
                    ? styles.badgeMicrosoft
                    : styles.badgeOffline,
                ].join(' ')}
              >
                {selectedAccount.value.type === 'microsoft' ? t('home.msa') : t('home.offline')}
              </span>
              <span class={styles.accountName}>{selectedAccount.value.username}</span>
            </div>
          ) : (
            <div class={styles.noAccount}>
              {t('home.noAccountSelected')}{' '}
              <a class={styles.link} onClick={() => router.push('/accounts')}>
                {t('home.addAnAccount')}
              </a>
            </div>
          )}

          <button
            class={styles.launchBtn}
            disabled={launching.value || !selectedAccount.value || !store.selectedVersion}
            onClick={handleLaunch}
          >
            <Play size={16} /> {launching.value ? t('home.launching') : t('home.launchBtn')}
          </button>

          {launchError.value && (
            <p class={styles.launchHint}>{launchError.value}</p>
          )}

          {!store.selectedVersion && (
            <p class={styles.hint}>{t('home.selectVersionHint')}</p>
          )}
        </div>
      </div>
    )
  },
})
