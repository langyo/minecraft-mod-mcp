import { defineComponent, ref, computed, onMounted } from 'vue'

import { RefreshCw, Search, Download, CheckCircle, Tag, Zap, Play } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'

import type { ManifestVersion, VersionInfo, Loader } from '@/types'
import styles from './InstallView.module.scss'
import { fetchRemoteVersions, installVersion, listInstalledVersions, launchGame } from '@/api/versions'
import { useLauncherStore } from '@/stores'
import { getLoaders } from '@/types'

type FilterType = 'all' | 'release' | 'snapshot'

const loaderColors: Record<Loader, string> = {
  forge: 'var(--color-loader-forge)',
  neoforge: 'var(--color-loader-neoforge)',
  fabric: 'var(--color-loader-fabric)',
}

function getDefaultLoader(v: VersionInfo): string | undefined {
  const loaders = getLoaders(v)
  return loaders[0]
}

export default defineComponent({
  setup() {
    const { t } = useI18n()
    const store = useLauncherStore()
    const filter = ref<FilterType>('all')
    const search = ref('')
    const installing = ref<string | null>(null)
    const installError = ref<string | null>(null)
    const loadError = ref<string | null>(null)
    const launching = ref(false)
    const launchError = ref<string | null>(null)

    const localVersions = computed(() => store.versions)

    const versions = computed(() => {
      let list = store.remoteVersions
      if (filter.value === 'release') {
        list = list.filter((v) => v.type === 'release')
      } else if (filter.value === 'snapshot') {
        list = list.filter((v) => v.type === 'snapshot')
      }
      if (search.value.trim()) {
        const q = search.value.trim().toLowerCase()
        list = list.filter((v) => v.id.toLowerCase().includes(q))
      }
      return list
    })

    const installedSet = computed(() => new Set(store.installedVersions))

    const selectedAccount = computed(() => {
      const cfg = store.config
      if (!cfg || !cfg.selected_account) return null
      return cfg.accounts.find((a) => a.uuid === cfg.selected_account) ?? null
    })

    async function handleRefresh() {
      loadError.value = null
      try {
        await store.fetchRemote()
        await store.fetchInstalled()
      } catch (e) {
        loadError.value = String(e)
      }
    }

    async function handleInstall(v: ManifestVersion) {
      installing.value = v.id
      installError.value = null
      try {
        await installVersion(v.id, v.url)
        await store.fetchInstalled()
      } catch (e) {
        installError.value = String(e)
      } finally {
        installing.value = null
      }
    }

    async function handleLaunch(v: VersionInfo) {
      if (!store.config?.selected_account) {
        launchError.value = t('home.noAccount')
        return
      }
      launching.value = true
      launchError.value = null
      try {
        await launchGame(v.version_id, getDefaultLoader(v))
        await store.fetchProcesses()
      } catch (e) {
        launchError.value = String(e)
      } finally {
        launching.value = false
      }
    }

    function formatDate(dateStr: string) {
      try {
        return new Date(dateStr).toLocaleDateString()
      } catch {
        return dateStr.slice(0, 10)
      }
    }

    onMounted(() => {
      if (store.versions.length === 0) store.fetchVersions()
      if (store.remoteVersions.length === 0) {
        handleRefresh()
      } else {
        store.fetchInstalled()
      }
    })

    return () => (
      <div class={styles.install}>
        <h1 class={styles.pageTitle}>{t('install.title')}</h1>
        <p class={styles.pageSubtitle}>{t('install.subtitle')}</p>

        {localVersions.value.length > 0 && (
          <div class={styles.localSection}>
            <h2 class={styles.localTitle}>{t('install.localVersions')}</h2>
            <div class={styles.localList}>
              {localVersions.value.map((v) => (
                <div key={v.mc_version} class={styles.localCard}>
                  <div class={styles.localMain}>
                    <div class={styles.localHeader}>
                      <span class={styles.localMcVer}>{v.mc_version}</span>
                      <span class={styles.localJava}>{t('common.jdk')} {v.java}</span>
                    </div>
                    <div class={styles.localLoaders}>
                      {getLoaders(v).map((loader: Loader) => (
                        <span
                          key={loader}
                          class={styles.loaderTag}
                          style={{ color: loaderColors[loader] }}
                        >
                          {t(`loaders.${loader}`)}
                        </span>
                      ))}
                    </div>
                  </div>
                  <button
                    class={styles.btnLaunch}
                    disabled={launching.value || !selectedAccount.value}
                    onClick={() => handleLaunch(v)}
                  >
                    <Play size={14} /> {t('install.launch')}
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}

        {launchError.value && <p class={styles.error}>{launchError.value}</p>}

        <div class={styles.remoteSection}>
          <div class={styles.toolbar}>
            <div class={styles.searchWrapper}>
              <Search size={14} class={styles.searchIcon} />
              <input
                class={styles.searchInput}
                type="text"
                placeholder={t('install.search')}
                value={search.value}
                onInput={(e) => { search.value = (e.target as HTMLInputElement).value }}
              />
            </div>
            <div class={styles.filterGroup}>
              {(['all', 'release', 'snapshot'] as FilterType[]).map((f) => (
                <button
                  key={f}
                  class={[styles.filterBtn, filter.value === f && styles.filterBtnActive].filter(Boolean).join(' ')}
                  onClick={() => { filter.value = f }}
                >
                  {t(`install.${f}`)}
                </button>
              ))}
            </div>
            <button class={styles.btn} onClick={handleRefresh} disabled={store.loading}>
              <RefreshCw size={14} /> {t('install.refresh')}
            </button>
          </div>

          {installError.value && <p class={styles.error}>{installError.value}</p>}
          {loadError.value && <div class={styles.error}>{loadError.value}</div>}

          <div class={styles.versionTable}>
            <div class={[styles.versionRow, styles.versionRowHeader].join(' ')}>
              <span>{t('install.version')}</span>
              <span class={styles.colCenter}>{t('install.type')}</span>
              <span class={styles.colCenter}>{t('install.released')}</span>
              <span class={styles.colAction}>{t('install.action')}</span>
            </div>

            {store.loading ? (
              <div class={styles.loading}>{t('install.loadingVersions')}</div>
            ) : versions.value.length === 0 ? (
              <div class={styles.empty}>{t('install.noVersions')}</div>
            ) : (
              versions.value.map((v) => (
                <div key={v.id} class={styles.versionRow}>
                  <span class={styles.colId}>{v.id}</span>
                    <span class={styles.colType}>
                      <span
                        class={[
                          styles.typeBadge,
                          v.type === 'release' ? styles.typeRelease
                            : v.type === 'snapshot' ? styles.typeSnapshot
                            : styles.typeOther,
                        ].join(' ')}
                      >
                        {v.type === 'release' ? <Tag size={12} /> : v.type === 'snapshot' ? <Zap size={12} /> : null} {v.type}
                      </span>
                    </span>
                  <span class={styles.colDate}>{formatDate(v.releaseTime)}</span>
                  <span class={styles.colAction}>
                    {installedSet.value.has(v.id) ? (
                      <span class={styles.installedBadge}><CheckCircle size={14} /> {t('install.installed')}</span>
                    ) : (
                      <button
                        class={styles.btnInstall}
                        disabled={installing.value === v.id}
                        onClick={() => handleInstall(v)}
                      >
                        <Download size={14} /> {installing.value === v.id ? t('install.installing') : t('install.install')}
                      </button>
                    )}
                  </span>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    )
  },
})
