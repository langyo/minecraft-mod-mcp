import { defineComponent } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import { useLauncherStore } from '@/stores'
import VersionItem from '@/components/VersionItem'
import type { VersionInfo } from '@/types'

import styles from './Sidebar.module.scss'

const navItems = [
  { path: '/', icon: '\u{1F3E0}', labelKey: 'nav.home' },
  { path: '/install', icon: '\u{1F4E6}', labelKey: 'nav.install' },
  { path: '/accounts', icon: '\u{1F464}', labelKey: 'nav.accounts' },
  { path: '/settings', icon: '\u{2699}\u{FE0F}', labelKey: 'nav.settings' },
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
              <span class={styles.navIcon}>{item.icon}</span>
              <span class={styles.navLabel}>{t(item.labelKey)}</span>
            </a>
          ))}
        </nav>

        <div class={styles.separator} />

        <div class={styles.sectionTitle}>{t('sidebar.versions')}</div>

        <div class={styles.versionList}>
          {store.loading ? (
            <div style={{ padding: '24px 16px', textAlign: 'center', fontSize: 'var(--font-sm)', color: 'var(--color-info)' }}>
              {t('common.loading')}
            </div>
          ) : store.error ? (
            <div style={{ padding: '24px 16px', textAlign: 'center', fontSize: 'var(--font-sm)', color: 'var(--color-error)' }}>
              {store.error}
            </div>
          ) : (
            store.versions.map((v) => (
              <VersionItem
                key={v.mc_version}
                version={v}
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
