import { defineComponent } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useLauncherStore } from '@/stores'
import VersionItem from '@/components/VersionItem'
import type { VersionInfo } from '@/types'

import styles from './Sidebar.module.scss'

const navItems = [
  { path: '/', icon: '\u{1F3E0}', label: 'Home' },
  { path: '/install', icon: '\u{1F4E6}', label: 'Install Version' },
  { path: '/accounts', icon: '\u{1F464}', label: 'Accounts' },
  { path: '/settings', icon: '\u{2699}\u{FE0F}', label: 'Settings' },
]

export default defineComponent({
  emits: ['select'],
  setup(_, { emit }) {
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
              <span class={styles.navLabel}>{item.label}</span>
            </a>
          ))}
        </nav>

        <div class={styles.separator} />

        <div class={styles.sectionTitle}>Versions</div>

        <div class={styles.versionList}>
          {store.loading ? (
            <div style={{ padding: '24px 16px', textAlign: 'center', fontSize: 'var(--font-sm)', color: 'var(--color-info)' }}>
              Loading...
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
            {store.mcpPort != null ? `MCP :${store.mcpPort}` : 'MCP Offline'}
          </span>
        </div>
      </div>
    )
  },
})
