import { defineComponent } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { LayoutDashboard, Activity, Package, User, Cpu } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'

import type { VersionInfo } from '@/types'
import styles from './Sidebar.module.scss'
import VersionItem from '@/components/VersionItem'
import { useLauncherStore } from '@/stores'

const navItems = [
  { path: '/', icon: LayoutDashboard, labelKey: 'nav.dashboard' },
  { path: '/mcp', icon: Activity, labelKey: 'nav.mcp' },
  { path: '/versions', icon: Package, labelKey: 'nav.versions' },
  { path: '/accounts', icon: User, labelKey: 'nav.accounts' },
  { path: '/vm', icon: Cpu, labelKey: 'nav.vm' },
]

export default defineComponent({
  emits: ['select'],
  setup(_, { emit }) {
    const { t } = useI18n()
    const store = useLauncherStore()
    const route = useRoute()
    const router = useRouter()

    function handleSelect(version: VersionInfo) {
      emit('select', version)
    }

    function handleNav(path: string) {
      router.push(path)
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

        <div class={styles.sectionTitle}>{t('sidebar.versions')}</div>

        <div class={styles.versionList}>
          {store.loading ? (
            <div class={styles.loadingState}>
              {t('common.loading')}
            </div>
          ) : store.error ? (
            <div style={{ padding: '24px 16px', textAlign: 'center', fontSize: 'var(--font-sm)', color: 'var(--color-error)' }}>
              {store.error}
            </div>
          ) : store.versions.length === 0 ? (
            <div style={{ padding: '24px 16px', textAlign: 'center', fontSize: 'var(--font-sm)', color: 'var(--color-info)' }}>
              {t('sidebar.noVersions')}
            </div>
          ) : (
            store.versions.map((v) => (
              <VersionItem
                key={v.mc_version}
                version={v}
                active={store.selectedVersion?.mc_version === v.mc_version}
                onSelect={() => handleSelect(v)}
              />
            ))
          )}
        </div>

        <div class={styles.sidebarStatus}>
          <span class={[styles.statusDot, store.mcpPort != null && styles.connected].join(' ')}>
            {store.mcpPort != null ? `${t('sidebar.mcpOnline')} :${store.mcpPort}` : t('sidebar.mcpOffline')}
          </span>
        </div>
      </div>
    )
  },
})
