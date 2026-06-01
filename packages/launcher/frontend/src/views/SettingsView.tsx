import { defineComponent, ref, reactive, onMounted, watch } from 'vue'

import { Search, Save, Check } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'

import type { JavaInfo, LauncherConfig, DownloadSource, Language, Theme } from '@/types'
import styles from './SettingsView.module.scss'
import { detectJavas } from '@/api/auth'
import { saveConfig } from '@/api/config'
import { useLauncherStore } from '@/stores'

export default defineComponent({
  setup() {
    const { t, locale } = useI18n()
    const store = useLauncherStore()
    const javas = ref<JavaInfo[]>([])
    const detecting = ref(false)
    const saved = ref(false)
    const saveError = ref<string | null>(null)

    const form = reactive<{
      java_dir: string
      java_version: string
      max_memory_mb: string
      min_memory_mb: string
      game_dir: string
      width: string
      height: string
      fullscreen: boolean
      java_args: string
      game_args: string
      download_source: DownloadSource
      language: Language
    }>({
      java_dir: '',
      java_version: '',
      max_memory_mb: '2048',
      min_memory_mb: '512',
      game_dir: '',
      width: '854',
      height: '480',
      fullscreen: false,
      java_args: '',
      game_args: '',
      download_source: 'bmclapi' as DownloadSource,
      language: 'zh-CN' as Language,
    })

    onMounted(() => {
      const cfg = store.config
      if (cfg) {
        populateForm(cfg)
      }
    })

    watch(() => store.config, (cfg) => {
      if (cfg) {
        populateForm(cfg)
      }
    })

    function populateForm(cfg: LauncherConfig) {
      form.java_dir = cfg.java_dir ?? ''
      form.java_version = cfg.java_version?.toString() ?? ''
      form.max_memory_mb = cfg.max_memory_mb.toString()
      form.min_memory_mb = cfg.min_memory_mb.toString()
      form.game_dir = cfg.game_dir ?? ''
      form.width = cfg.width.toString()
      form.height = cfg.height.toString()
      form.fullscreen = cfg.fullscreen
      form.java_args = cfg.java_args ?? ''
      form.game_args = cfg.game_args ?? ''
      form.download_source = cfg.download_source
      form.language = cfg.language
      locale.value = cfg.language
    }

    watch(() => form.language, (val) => {
      locale.value = val
    })

    async function handleDetect() {
      detecting.value = true
      try {
        javas.value = await detectJavas()
      } catch {
        javas.value = []
      } finally {
        detecting.value = false
      }
    }

    async function handleSave() {
      const cfg: LauncherConfig = {
        java_dir: form.java_dir || null,
        java_version: form.java_version ? parseInt(form.java_version) : null,
        max_memory_mb: parseInt(form.max_memory_mb) || 2048,
        min_memory_mb: parseInt(form.min_memory_mb) || 512,
        game_dir: form.game_dir || null,
        java_args: form.java_args || null,
        game_args: form.game_args || null,
        width: parseInt(form.width) || 854,
        height: parseInt(form.height) || 480,
        fullscreen: form.fullscreen,
        accounts: store.config?.accounts ?? [],
        selected_account: store.config?.selected_account ?? null,
        download_source: form.download_source,
        mcp_port: store.config?.mcp_port ?? null,
        language: form.language,
        theme: store.config?.theme ?? 'dark' as Theme,
        follow_system_theme: store.config?.follow_system_theme ?? true,
      }
      try {
        await saveConfig(cfg)
        saveError.value = null
        await store.fetchConfig()
        saved.value = true
        setTimeout(() => { saved.value = false }, 2000)
      } catch (e) {
        saveError.value = String(e)
      }
    }

    return () => (
      <div class={styles.settings}>
        <h1 class={styles.pageTitle}>{t('settings.title')}</h1>
        <p class={styles.pageSubtitle}>{t('settings.subtitle')}</p>

        <div class={styles.section}>
          <div class={styles.sectionTitle}>{t('settings.javaDetection')}</div>
          <div class={styles.btnRow}>
            <button class={styles.btn} disabled={detecting.value} onClick={handleDetect}>
              <Search size={14} /> {detecting.value ? t('settings.detecting') : t('settings.detectJavas')}
            </button>
          </div>
          {javas.value.length > 0 && (
            <div class={styles.javaList}>
              {javas.value.map((j) => (
                <div key={j.path} class={styles.javaItem}>
                  <span class={styles.javaVersion}><Check size={14} /> JDK {j.version}</span>
                  <span class={styles.javaPath}>{j.path}</span>
                </div>
              ))}
            </div>
          )}
        </div>

        <div class={styles.section}>
          <div class={styles.sectionTitle}>{t('settings.javaConfiguration')}</div>
          <div class={styles.formGrid}>
            <div class={styles.formGroup}>
              <label class={styles.formLabel}>{t('settings.javaPath')}</label>
              <input
                class={styles.input}
                type="text"
                placeholder={t('settings.autoDetect')}
                value={form.java_dir}
                onInput={(e) => { form.java_dir = (e.target as HTMLInputElement).value }}
              />
            </div>
            <div class={styles.formGroup}>
              <label class={styles.formLabel}>{t('settings.javaVersion')}</label>
              <select
                class={styles.select}
                value={form.java_version}
                onChange={(e) => { form.java_version = (e.target as HTMLSelectElement).value }}
              >
                <option value="">{t('settings.auto')}</option>
                <option value="8">8</option>
                <option value="16">16</option>
                <option value="17">17</option>
                <option value="21">21</option>
                <option value="25">25</option>
              </select>
            </div>
          </div>
        </div>

        <div class={styles.section}>
          <div class={styles.sectionTitle}>{t('settings.memory')}</div>
          <div class={styles.formGrid}>
            <div class={styles.formGroup}>
              <label class={styles.formLabel}>{t('settings.minMemory')}</label>
              <input
                class={styles.input}
                type="number"
                value={form.min_memory_mb}
                onInput={(e) => { form.min_memory_mb = (e.target as HTMLInputElement).value }}
              />
            </div>
            <div class={styles.formGroup}>
              <label class={styles.formLabel}>{t('settings.maxMemory')}</label>
              <input
                class={styles.input}
                type="number"
                value={form.max_memory_mb}
                onInput={(e) => { form.max_memory_mb = (e.target as HTMLInputElement).value }}
              />
            </div>
          </div>
        </div>

        <div class={styles.section}>
          <div class={styles.sectionTitle}>{t('settings.gameDir')}</div>
          <div class={styles.formGrid}>
            <div class={[styles.formGroup, styles.formGroupFull].join(' ')}>
              <label class={styles.formLabel}>{t('settings.gameDir')}</label>
              <input
                class={styles.input}
                type="text"
                placeholder={t('settings.defaultGameDir')}
                value={form.game_dir}
                onInput={(e) => { form.game_dir = (e.target as HTMLInputElement).value }}
              />
            </div>
          </div>
        </div>

        <div class={styles.section}>
          <div class={styles.sectionTitle}>{t('settings.window')}</div>
          <div class={styles.formGrid}>
            <div class={styles.formGroup}>
              <label class={styles.formLabel}>{t('settings.width')}</label>
              <input
                class={styles.input}
                type="number"
                value={form.width}
                onInput={(e) => { form.width = (e.target as HTMLInputElement).value }}
              />
            </div>
            <div class={styles.formGroup}>
              <label class={styles.formLabel}>{t('settings.height')}</label>
              <input
                class={styles.input}
                type="number"
                value={form.height}
                onInput={(e) => { form.height = (e.target as HTMLInputElement).value }}
              />
            </div>
            <div class={[styles.formGroup, styles.formGroupFull].join(' ')}>
              <div class={styles.toggleRow}>
                <div
                  class={[styles.toggle, form.fullscreen && styles.toggleActive].filter(Boolean).join(' ')}
                  onClick={() => { form.fullscreen = !form.fullscreen }}
                />
                <span class={styles.toggleLabel}>{t('settings.fullscreen')}</span>
              </div>
            </div>
          </div>
        </div>

        <div class={styles.section}>
          <div class={styles.sectionTitle}>{t('settings.extraArguments')}</div>
          <div class={styles.formGrid}>
            <div class={styles.formGroup}>
              <label class={styles.formLabel}>{t('settings.jvmArgs')}</label>
              <textarea
                class={styles.textarea}
                value={form.java_args}
                onInput={(e) => { form.java_args = (e.target as HTMLTextAreaElement).value }}
                placeholder={t('settings.jvmArgsPlaceholder')}
              />
            </div>
            <div class={styles.formGroup}>
              <label class={styles.formLabel}>{t('settings.gameArgs')}</label>
              <textarea
                class={styles.textarea}
                value={form.game_args}
                onInput={(e) => { form.game_args = (e.target as HTMLTextAreaElement).value }}
                placeholder={t('settings.gameArgsPlaceholder')}
              />
            </div>
          </div>
        </div>

        <div class={styles.section}>
          <div class={styles.sectionTitle}>{t('settings.downloadSource')}</div>
          <div class={styles.radioGroup}>
            <label
              class={styles.radioLabel}
              onClick={() => { form.download_source = 'mojang' }}
            >
              <span class={[styles.radioDot, form.download_source === 'mojang' && styles.radioDotActive].filter(Boolean).join(' ')} />
              {t('settings.mojang')}
            </label>
            <label
              class={styles.radioLabel}
              onClick={() => { form.download_source = 'bmclapi' }}
            >
              <span class={[styles.radioDot, form.download_source === 'bmclapi' && styles.radioDotActive].filter(Boolean).join(' ')} />
              {t('settings.bmclapi')}
            </label>
          </div>
        </div>

        <div class={styles.section}>
          <div class={styles.sectionTitle}>{t('settings.language')}</div>
          <select
            class={styles.narrowSelect}
            value={form.language}
            onChange={(e) => { form.language = (e.target as HTMLSelectElement).value as Language }}
          >
            <option value="zh-CN">简体中文</option>
            <option value="zh-TW">繁體中文</option>
            <option value="en-US">English</option>
            <option value="ja-JP">日本語</option>
            <option value="ko-KR">한국어</option>
            <option value="de-DE">Deutsch</option>
            <option value="fr-FR">Français</option>
            <option value="es-ES">Español</option>
          </select>
        </div>

        <div class={styles.btnRow}>
          <button class={[styles.btn, styles.btnSave].join(' ')} onClick={handleSave}>
            <Save size={14} /> {t('settings.save')}
          </button>
          {saved.value && <span class={styles.savedText}><Check size={14} /> {t('settings.saved')}</span>}
          {saveError.value && <span class={styles.errorText}>{saveError.value}</span>}
        </div>
      </div>
    )
  },
})
