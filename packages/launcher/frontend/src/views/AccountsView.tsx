import { defineComponent, ref, computed, onUnmounted } from 'vue'

import { Globe, User, Plus, Trash2, RefreshCw, X, Check, Copy, Shuffle } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'

import { useLauncherStore } from '@/stores'
import {
  startMicrosoftAuth,
  pollMicrosoftAuth,
  addOfflineAccount,
  removeAccount,
  selectAccount,
  refreshAccount,
} from '@/api/auth'
import type { DeviceCodeInfo } from '@/types'

import styles from './AccountsView.module.scss'

function generateRandomUUID(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

function copyToClipboard(text: string) {
  navigator.clipboard.writeText(text).catch(() => {})
}

export default defineComponent({
  setup() {
    const { t } = useI18n()
    const store = useLauncherStore()

    const showMsDialog = ref(false)
    const showOfflineDialog = ref(false)
    const deviceCodeInfo = ref<DeviceCodeInfo | null>(null)
    const authPolling = ref(false)
    const authError = ref<string | null>(null)
    const offlineUsername = ref('')
    const offlineUuid = ref('')
    const offlineError = ref<string | null>(null)
    const refreshingUuid = ref<string | null>(null)
    const actionError = ref<string | null>(null)
    const pollTimeout = ref<ReturnType<typeof setTimeout> | null>(null)

    const accounts = computed(() => store.config?.accounts ?? [])
    const selectedUuid = computed(() => store.config?.selected_account ?? null)

    onUnmounted(() => {
      if (pollTimeout.value) clearTimeout(pollTimeout.value)
    })

    async function handleStartMicrosoft() {
      showMsDialog.value = true
      authError.value = null
      deviceCodeInfo.value = null
      authPolling.value = true
      try {
        const info = await startMicrosoftAuth()
        deviceCodeInfo.value = info
        startPoll(info.device_code, info.expires_in)
      } catch (e) {
        authError.value = String(e)
        authPolling.value = false
      }
    }

    async function startPoll(deviceCode: string, expiresInSeconds: number) {
      authPolling.value = true
      authError.value = null
      pollTimeout.value = setTimeout(() => {
        authError.value = t('accounts.authExpired')
        authPolling.value = false
      }, expiresInSeconds * 1000)
      try {
        await pollMicrosoftAuth(deviceCode)
        if (pollTimeout.value) clearTimeout(pollTimeout.value)
        authPolling.value = false
        showMsDialog.value = false
        await store.fetchConfig()
      } catch (e) {
        if (pollTimeout.value) clearTimeout(pollTimeout.value)
        authError.value = String(e)
        authPolling.value = false
      }
    }

    function closeMsDialog() {
      showMsDialog.value = false
      authPolling.value = false
    }

    function handleOpenOfflineDialog() {
      offlineUsername.value = ''
      offlineUuid.value = generateRandomUUID()
      offlineError.value = null
      showOfflineDialog.value = true
    }

    async function handleAddOffline() {
      const name = offlineUsername.value.trim()
      if (!name) return
      offlineError.value = null
      try {
        await addOfflineAccount(name, offlineUuid.value || undefined)
        showOfflineDialog.value = false
        await store.fetchConfig()
      } catch (e) {
        offlineError.value = String(e)
      }
    }

    async function handleRemove(uuid: string) {
      if (!confirm(t('accounts.confirmRemove'))) return
      actionError.value = null
      try {
        await removeAccount(uuid)
        await store.fetchConfig()
      } catch (e) {
        actionError.value = String(e)
      }
    }

    async function handleSelect(uuid: string) {
      actionError.value = null
      try {
        await selectAccount(uuid)
        await store.fetchConfig()
      } catch (e) {
        actionError.value = String(e)
      }
    }

    async function handleRefresh(uuid: string) {
      refreshingUuid.value = uuid
      actionError.value = null
      try {
        await refreshAccount(uuid)
        await store.fetchConfig()
      } catch (e) {
        actionError.value = String(e)
      } finally {
        refreshingUuid.value = null
      }
    }

    return () => (
      <div class={styles.accounts}>
        <h1 class={styles.pageTitle}>{t('accounts.title')}</h1>
        <p class={styles.pageSubtitle}>{t('accounts.subtitle')}</p>

        <div class={styles.section}>
          <div class={styles.sectionHeader}>
            <span class={styles.sectionTitle}>{t('accounts.yourAccounts')}</span>
            <div class={styles.btnRow}>
              <button class={styles.btn} onClick={handleStartMicrosoft}>
                <Plus size={14} /> {t('accounts.addMicrosoft')}
              </button>
              <button class={styles.btn} onClick={handleOpenOfflineDialog}>
                <Plus size={14} /> {t('accounts.addOffline')}
              </button>
            </div>
          </div>

          {actionError.value && (
            <div class={styles.errorRow}>
              <span class={styles.errorText}>{actionError.value}</span>
              <button class={styles.copyBtn} onClick={() => copyToClipboard(actionError.value!)} title={t('common.copy')}>
                <Copy size={12} />
              </button>
            </div>
          )}

          <div class={styles.accountList}>
            {accounts.value.length === 0 ? (
              <p class={styles.emptyHint}>{t('accounts.noAccounts')}</p>
            ) : (
              accounts.value.map((account) => (
                <div
                  key={account.uuid}
                  class={[
                    styles.accountItem,
                    account.uuid === selectedUuid.value && styles.accountItemSelected,
                  ].filter(Boolean).join(' ')}
                  onClick={() => handleSelect(account.uuid)}
                >
                  <span
                    class={[
                      styles.accountBadge,
                      account.type === 'microsoft' ? styles.badgeMicrosoft : styles.badgeOffline,
                    ].join(' ')}
                  >
                    {account.type === 'microsoft' ? <Globe size={14} /> : <User size={14} />} {account.type === 'microsoft' ? t('accounts.msa') : t('accounts.offline')}
                  </span>
                  <div class={styles.accountDetails}>
                    <div class={styles.accountName}>{account.username}</div>
                    <div class={styles.accountUuid}>{account.uuid}</div>
                  </div>
                  <div class={styles.accountActions}>
                    {account.type === 'microsoft' && (
                      <button
                        class={[styles.btn, styles.btnSm].join(' ')}
                        disabled={refreshingUuid.value === account.uuid}
                        onClick={(e) => { e.stopPropagation(); handleRefresh(account.uuid) }}
                      >
                        <RefreshCw size={14} /> {refreshingUuid.value === account.uuid ? t('accounts.refreshing') : t('accounts.refresh')}
                      </button>
                    )}
                    <button
                      class={[styles.btn, styles.btnSm, styles.btnDanger].join(' ')}
                      onClick={(e) => { e.stopPropagation(); handleRemove(account.uuid) }}
                    >
                      <Trash2 size={14} /> {t('accounts.remove')}
                    </button>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        {showMsDialog.value && (
          <div class={styles.modalOverlay} onClick={closeMsDialog}>
            <div class={styles.modal} onClick={(e) => e.stopPropagation()}>
              <h3 class={styles.modalTitle}>{t('accounts.authTitle')}</h3>
              <div class={styles.modalBody}>
                {authPolling.value && !deviceCodeInfo.value ? (
                  <p class={styles.statusText}>{t('accounts.authRequesting')}</p>
                ) : deviceCodeInfo.value ? (
                  <>
                    <div class={styles.codeDisplay}>
                      <div class={styles.codeText}>{deviceCodeInfo.value.user_code}</div>
                        <div class={styles.codeUrl}>
                        {t('accounts.authStep1')}{' '}
                        <a
                          class={styles.primaryColor}
                          href={deviceCodeInfo.value.verification_uri}
                          target="_blank"
                          rel="noopener"
                        >
                          {deviceCodeInfo.value.verification_uri}
                        </a>
                      </div>
                    </div>
                    <p class={styles.statusText}>{t('accounts.authPolling')}</p>
                  </>
                ) : authError.value ? (
                  <div class={styles.errorRow}>
                    <span class={styles.errorText}>{authError.value}</span>
                    <button class={styles.copyBtn} onClick={() => copyToClipboard(authError.value!)} title={t('common.copy')}>
                      <Copy size={12} />
                    </button>
                  </div>
                ) : null}
              </div>
              <div class={styles.modalActions}>
                <button class={styles.btn} onClick={closeMsDialog}><X size={14} /> {t('accounts.cancel')}</button>
              </div>
            </div>
          </div>
        )}

        {showOfflineDialog.value && (
          <div class={styles.modalOverlay} onClick={() => { showOfflineDialog.value = false }}>
            <div class={styles.modal} onClick={(e) => e.stopPropagation()}>
              <h3 class={styles.modalTitle}>{t('accounts.addOfflineTitle')}</h3>
              <div class={styles.modalBody}>
                <div class={styles.formGroup}>
                  <label class={styles.formLabel}>{t('accounts.usernamePlaceholder')}</label>
                  <input
                    class={styles.input}
                    type="text"
                    placeholder={t('accounts.usernamePlaceholder')}
                    value={offlineUsername.value}
                    onInput={(e) => { offlineUsername.value = (e.target as HTMLInputElement).value }}
                    onKeydown={(e) => { if (e.key === 'Enter') handleAddOffline() }}
                  />
                </div>
                <div class={styles.formGroup}>
                  <label class={styles.formLabel}>UUID</label>
                  <div class={styles.uuidRow}>
                    <input
                      class={[styles.input, styles.uuidInput].join(' ')}
                      type="text"
                      placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                      value={offlineUuid.value}
                      onInput={(e) => { offlineUuid.value = (e.target as HTMLInputElement).value }}
                    />
                    <button
                      class={styles.btnIcon}
                      onClick={() => { offlineUuid.value = generateRandomUUID() }}
                      title={t('accounts.randomUuid')}
                    >
                      <Shuffle size={14} />
                    </button>
                  </div>
                </div>
                {offlineError.value && (
                  <div class={styles.errorRow}>
                    <span class={styles.errorText}>{offlineError.value}</span>
                    <button class={styles.copyBtn} onClick={() => copyToClipboard(offlineError.value!)} title={t('common.copy')}>
                      <Copy size={12} />
                    </button>
                  </div>
                )}
              </div>
              <div class={styles.modalActions}>
                <button class={styles.btn} onClick={() => { showOfflineDialog.value = false }}>{t('accounts.cancel')}</button>
                <button class={[styles.btn, styles.btnPrimary].join(' ')} onClick={handleAddOffline}><Check size={14} /> {t('accounts.add')}</button>
              </div>
            </div>
          </div>
        )}
      </div>
    )
  },
})
