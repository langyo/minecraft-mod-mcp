import { defineComponent, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Package } from 'lucide-vue-next'

import { useLauncherStore } from '@/stores'
import { getLoaders } from '@/types'
import { launchGame } from '@/api/versions'

import styles from './HomeView.module.scss'

function getDefaultLoader(v: import('@/types').VersionInfo): string | undefined {
  const loaders = getLoaders(v)
  return loaders[0]
}

export default defineComponent({
  setup() {
    const { t } = useI18n()
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
      } catch (e) {
        launchError.value = String(e)
      } finally {
        launching.value = false
      }
    }

    return () => (
      <div class={styles.home}>
        <div class={styles.welcome}>
          <div class={styles.welcomeIcon}><Package size={48} /></div>
          <h2>{t('app.name')}</h2>
          <p class={styles.subtitle}>{t('home.subtitle')}</p>

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
            {launching.value ? t('home.launching') : t('home.launchBtn')}
          </button>

          {launchError.value && (
            <p class={styles.launchHint}>{launchError.value}</p>
          )}

          <div class={styles.stats}>
            <div class={styles.stat}>
              <span class={styles.statValue}>{store.versions.length}</span>
              <span class={styles.statLabel}>{t('home.versions')}</span>
            </div>
            <div class={styles.stat}>
              <span class={styles.statValue}>
                {store.versions.reduce((acc, v) => acc + getLoaders(v).length, 0)}
              </span>
              <span class={styles.statLabel}>{t('home.profiles')}</span>
            </div>
            <div class={styles.stat}>
              <span class={[styles.statValue, store.mcpPort != null && styles.connected].filter(Boolean).join(' ')}>
                {store.mcpPort ?? '--'}
              </span>
              <span class={styles.statLabel}>{t('home.mcpPort')}</span>
            </div>
          </div>

          <p class={styles.hint}>{t('home.selectVersionHint')}</p>
        </div>
      </div>
    )
  },
})
