import { defineComponent, ref, computed } from 'vue'
import { useRouter } from 'vue-router'

import { useLauncherStore } from '@/stores'
import { getLoaders } from '@/types'
import { launchGame } from '@/api/versions'

import styles from './HomeView.module.scss'

export default defineComponent({
  setup() {
    const store = useLauncherStore()
    const router = useRouter()
    const launching = ref(false)
    const launchError = ref<string | null>(null)

    const selectedAccount = computed(() => {
      const cfg = store.config
      if (!cfg || !cfg.selected_account) return null
      return cfg.accounts.find((a) => a.uuid === cfg.selected_account) ?? null
    })

    async function handleLaunch() {
      launching.value = true
      launchError.value = null
      try {
        await launchGame('1.20.1-forge-47.3.0')
      } catch (e) {
        launchError.value = String(e)
      } finally {
        launching.value = false
      }
    }

    return () => (
      <div class={styles.home}>
        <div class={styles.welcome}>
          <div class={styles.welcomeIcon}>📦</div>
          <h2>MMML</h2>
          <p class={styles.subtitle}>Minecraft Mod MCP Launcher</p>

          {selectedAccount.value ? (
            <div class={styles.accountInfo}>
              <span
                class={[
                  styles.accountBadge,
                  selectedAccount.value.type === 'microsoft'
                    ? styles.badgeMicrosoft
                    : styles.badgeOffline,
                ].join(' ')}
              >
                {selectedAccount.value.type === 'microsoft' ? 'MSA' : 'Offline'}
              </span>
              <span class={styles.accountName}>{selectedAccount.value.username}</span>
            </div>
          ) : (
            <div class={styles.noAccount}>
              No account selected —{' '}
              <a class={styles.link} onClick={() => router.push('/accounts')}>
                Add an account
              </a>
            </div>
          )}

          <button
            class={styles.launchBtn}
            disabled={launching.value || !selectedAccount.value}
            onClick={handleLaunch}
          >
            {launching.value ? 'Launching...' : 'LAUNCH'}
          </button>

          {launchError.value && (
            <p class={styles.noVersion}>{launchError.value}</p>
          )}

          <div class={styles.stats}>
            <div class={styles.stat}>
              <span class={styles.statValue}>{store.versions.length}</span>
              <span class={styles.statLabel}>Versions</span>
            </div>
            <div class={styles.stat}>
              <span class={styles.statValue}>
                {store.versions.reduce((acc, v) => acc + getLoaders(v).length, 0)}
              </span>
              <span class={styles.statLabel}>Profiles</span>
            </div>
            <div class={styles.stat}>
              <span class={[styles.statValue, store.mcpPort != null && styles.connected].filter(Boolean).join(' ')}>
                {store.mcpPort ?? '--'}
              </span>
              <span class={styles.statLabel}>MCP Port</span>
            </div>
          </div>

          <p class={styles.hint}>Select a version from the sidebar for details</p>
        </div>
      </div>
    )
  },
})
