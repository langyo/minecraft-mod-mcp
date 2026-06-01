import { defineComponent, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { LayoutDashboard, Activity, Package, User, Cpu, Square } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'

import styles from './Sidebar.module.scss'
import { killProcess } from '@/api/versions'
import { useLauncherStore } from '@/stores'

const navItems = [
  { path: '/', icon: LayoutDashboard, labelKey: 'nav.dashboard' },
  { path: '/mcp', icon: Activity, labelKey: 'nav.mcp' },
  { path: '/versions', icon: Package, labelKey: 'nav.versions' },
  { path: '/accounts', icon: User, labelKey: 'nav.accounts' },
  { path: '/vm', icon: Cpu, labelKey: 'nav.vm' },
]

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
    const route = useRoute()
    const router = useRouter()
    let pollTimer: ReturnType<typeof setInterval> | null = null

    onMounted(() => {
      pollTimer = setInterval(() => {
        store.fetchProcesses()
      }, 3000)
    })

    onUnmounted(() => {
      if (pollTimer) clearInterval(pollTimer)
    })

    function handleNav(path: string) {
      router.push(path)
    }

    async function handleKill(id: number) {
      try {
        await killProcess(id)
        await store.fetchProcesses()
      } catch {}
    }

    return () => (
      <div class={styles.sidebarContent}>
        <nav class={styles.nav}>
          {navItems.map((item) => (
            <a
              key={item.path}
              class={[
                styles.navItem,
                route.path === item.path && styles.navItemActive,
              ].filter(Boolean).join(' ')}
              onClick={(e) => {
                e.preventDefault()
                handleNav(item.path)
              }}
              href={item.path}
            >
              <span class={styles.navIcon}>{<item.icon size={16} />}</span>
              <span class={styles.navLabel}>{t(item.labelKey)}</span>
            </a>
          ))}
        </nav>

        <div class={styles.separator} />

        <div class={styles.sectionTitle}>{t('sidebar.instances')}</div>

        <div class={styles.instanceList}>
          {store.runningProcesses.length === 0 ? (
            <div class={styles.emptyState}>
              {t('sidebar.noInstances')}
            </div>
          ) : (
            store.runningProcesses.map((proc) => (
              <div key={proc.id} class={styles.instanceCard}>
                <div class={styles.instanceHeader}>
                  <span class={styles.instanceVersion}>{proc.version_id}</span>
                  <span class={styles.instanceLoader}>{proc.loader}</span>
                </div>
                <div class={styles.instanceMeta}>
                  <span>PID {proc.pid}</span>
                  <span>{formatUptime(proc.started_at)}</span>
                  {proc.mcp_port != null && (
                    <span class={styles.instanceMcp}>MCP :{proc.mcp_port}</span>
                  )}
                </div>
                <div class={styles.instanceActions}>
                  {proc.mcp_port != null && (
                    <button
                      class={[styles.instanceBtn, styles.btnConnect].join(' ')}
                      onClick={() => router.push('/mcp')}
                    >
                      {t('sidebar.connect')}
                    </button>
                  )}
                  <button
                    class={[styles.instanceBtn, styles.btnKill].join(' ')}
                    onClick={() => handleKill(proc.id)}
                  >
                    <Square size={10} /> {t('sidebar.kill')}
                  </button>
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    )
  },
})
