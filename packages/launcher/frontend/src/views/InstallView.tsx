import { defineComponent, ref, computed, onMounted } from 'vue'

import { RefreshCw, Search, Download, CheckCircle, Tag, Zap } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'

import type { ManifestVersion } from '@/types'
import styles from './InstallView.module.scss'
import { fetchRemoteVersions, installVersion, listInstalledVersions } from '@/api/versions'
import { useLauncherStore } from '@/stores'

type FilterType = 'all' | 'release' | 'snapshot'

export default defineComponent({
  setup() {
    const { t } = useI18n()
    const store = useLauncherStore()
    const filter = ref<FilterType>('all')
    const search = ref('')
    const installing = ref<string | null>(null)
    const installError = ref<string | null>(null)
    const loadError = ref<string | null>(null)

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

    function formatDate(dateStr: string) {
      try {
        return new Date(dateStr).toLocaleDateString()
      } catch {
        return dateStr.slice(0, 10)
      }
    }

    onMounted(() => {
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
    )
  },
})
