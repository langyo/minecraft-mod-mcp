import { defineComponent, ref, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Activity, Wifi, WifiOff, RefreshCw, Plug, Server } from 'lucide-vue-next'

import { useLauncherStore } from '@/stores'

import styles from './MCPMonitorView.module.scss'

export default defineComponent({
  setup() {
    const { t } = useI18n()
    const store = useLauncherStore()
    let pollTimer: ReturnType<typeof setInterval> | null = null

    onMounted(() => {
      store.fetchProcesses()
      store.fetchMcpPort()
      pollTimer = setInterval(() => {
        store.fetchProcesses()
        store.fetchMcpPort()
      }, 3000)
    })

    onUnmounted(() => {
      if (pollTimer) clearInterval(pollTimer)
    })

    const mcpReady = () => store.mcpPort != null
    const connectedProcesses = () => store.runningProcesses.filter((p) => p.mcp_port != null)
    const unconnectedProcesses = () => store.runningProcesses.filter((p) => p.mcp_port == null)

    return () => (
      <div class={styles.mcp}>
        <div class={styles.header}>
          <h1 class={styles.pageTitle}>{t('mcp.title')}</h1>
          <p class={styles.pageSubtitle}>{t('mcp.subtitle')}</p>
        </div>

        <div class={styles.statusBanner}>
          <div class={[styles.statusIndicator, mcpReady() ? styles.online : styles.offline].join(' ')}>
            {mcpReady() ? <Wifi size={20} /> : <WifiOff size={20} />}
          </div>
          <div class={styles.statusInfo}>
            <span class={styles.statusLabel}>
              {mcpReady() ? t('mcp.serverOnline') : t('mcp.serverOffline')}
            </span>
            {mcpReady() && (
              <span class={styles.statusDetail}>
                {t('mcp.listeningOn', { port: store.mcpPort })}
              </span>
            )}
          </div>
          <button
            class={styles.iconBtn}
            onClick={() => { store.fetchProcesses(); store.fetchMcpPort() }}
          >
            <RefreshCw size={14} />
          </button>
        </div>

        <div class={styles.section}>
          <h2 class={styles.sectionTitle}>
            <Plug size={14} /> {t('mcp.connectedInstances')}
          </h2>
          {connectedProcesses().length === 0 ? (
            <div class={styles.emptyState}>{t('mcp.noConnected')}</div>
          ) : (
            <div class={styles.instanceList}>
              {connectedProcesses().map((proc) => (
                <div key={proc.id} class={[styles.instanceCard, styles.connected].join(' ')}>
                  <div class={styles.instanceInfo}>
                    <span class={styles.instanceVersion}>{proc.version_id}</span>
                    <span class={styles.instanceLoader}>{proc.loader}</span>
                  </div>
                  <div class={styles.instanceMeta}>
                    <span class={styles.portBadge}>:{proc.mcp_port}</span>
                    <span class={styles.pid}>PID {proc.pid}</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        <div class={styles.section}>
          <h2 class={styles.sectionTitle}>
            <Server size={14} /> {t('mcp.runningNotConnected')}
          </h2>
          {unconnectedProcesses().length === 0 ? (
            <div class={styles.emptyState}>{t('mcp.noUnconnected')}</div>
          ) : (
            <div class={styles.instanceList}>
              {unconnectedProcesses().map((proc) => (
                <div key={proc.id} class={styles.instanceCard}>
                  <div class={styles.instanceInfo}>
                    <span class={styles.instanceVersion}>{proc.version_id}</span>
                    <span class={styles.instanceLoader}>{proc.loader}</span>
                  </div>
                  <div class={styles.instanceMeta}>
                    <span class={styles.pid}>PID {proc.pid}</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {!mcpReady() && (
          <div class={styles.setupHint}>
            <Activity size={16} />
            <span>{t('mcp.setupHint')}</span>
          </div>
        )}
      </div>
    )
  },
})
