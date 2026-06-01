import { defineComponent, ref, reactive, onMounted } from 'vue'

import { useLauncherStore } from '@/stores'
import { saveConfig } from '@/api/config'
import { detectJavas } from '@/api/auth'
import type { JavaInfo, LauncherConfig } from '@/types'

import styles from './SettingsView.module.scss'

export default defineComponent({
  setup() {
    const store = useLauncherStore()
    const javas = ref<JavaInfo[]>([])
    const detecting = ref(false)
    const saved = ref(false)

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
      download_source: string
      language: string
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
      download_source: 'bmclapi',
      language: 'zh-CN',
    })

    onMounted(() => {
      const cfg = store.config
      if (cfg) {
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
      }
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
      }
      try {
        await saveConfig(cfg)
        await store.fetchConfig()
        saved.value = true
        setTimeout(() => { saved.value = false }, 2000)
      } catch (e) {
        console.error(e)
      }
    }

    return () => (
      <div class={styles.settings}>
        <h1 class={styles.pageTitle}>Settings</h1>
        <p class={styles.pageSubtitle}>Configure your launcher</p>

        <div class={styles.section}>
          <div class={styles.sectionTitle}>Java Detection</div>
          <div class={styles.btnRow}>
            <button class={styles.btn} disabled={detecting.value} onClick={handleDetect}>
              {detecting.value ? 'Detecting...' : 'Detect JDKs'}
            </button>
          </div>
          {javas.value.length > 0 && (
            <div class={styles.javaList}>
              {javas.value.map((j) => (
                <div key={j.path} class={styles.javaItem}>
                  <span class={styles.javaVersion}>JDK {j.version}</span>
                  <span class={styles.javaPath}>{j.path}</span>
                </div>
              ))}
            </div>
          )}
        </div>

        <div class={styles.section}>
          <div class={styles.sectionTitle}>Java Configuration</div>
          <div class={styles.formGrid}>
            <div class={styles.formGroup}>
              <label class={styles.formLabel}>Java Path</label>
              <input
                class={styles.input}
                type="text"
                placeholder="Auto-detect"
                value={form.java_dir}
                onInput={(e) => { form.java_dir = (e.target as HTMLInputElement).value }}
              />
            </div>
            <div class={styles.formGroup}>
              <label class={styles.formLabel}>Java Version</label>
              <select
                class={styles.select}
                value={form.java_version}
                onChange={(e) => { form.java_version = (e.target as HTMLSelectElement).value }}
              >
                <option value="">Auto</option>
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
          <div class={styles.sectionTitle}>Memory</div>
          <div class={styles.formGrid}>
            <div class={styles.formGroup}>
              <label class={styles.formLabel}>Min Memory (MB)</label>
              <input
                class={styles.input}
                type="number"
                value={form.min_memory_mb}
                onInput={(e) => { form.min_memory_mb = (e.target as HTMLInputElement).value }}
              />
            </div>
            <div class={styles.formGroup}>
              <label class={styles.formLabel}>Max Memory (MB)</label>
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
          <div class={styles.sectionTitle}>Game Directory</div>
          <div class={styles.formGrid}>
            <div class={[styles.formGroup, styles.formGroupFull].join(' ')}>
              <label class={styles.formLabel}>Game Directory</label>
              <input
                class={styles.input}
                type="text"
                placeholder="Default ~/.minecraft"
                value={form.game_dir}
                onInput={(e) => { form.game_dir = (e.target as HTMLInputElement).value }}
              />
            </div>
          </div>
        </div>

        <div class={styles.section}>
          <div class={styles.sectionTitle}>Window Size</div>
          <div class={styles.formGrid}>
            <div class={styles.formGroup}>
              <label class={styles.formLabel}>Width</label>
              <input
                class={styles.input}
                type="number"
                value={form.width}
                onInput={(e) => { form.width = (e.target as HTMLInputElement).value }}
              />
            </div>
            <div class={styles.formGroup}>
              <label class={styles.formLabel}>Height</label>
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
                <span class={styles.toggleLabel}>Fullscreen</span>
              </div>
            </div>
          </div>
        </div>

        <div class={styles.section}>
          <div class={styles.sectionTitle}>Extra Arguments</div>
          <div class={styles.formGrid}>
            <div class={styles.formGroup}>
              <label class={styles.formLabel}>JVM Arguments</label>
              <textarea
                class={styles.textarea}
                value={form.java_args}
                onInput={(e) => { form.java_args = (e.target as HTMLTextAreaElement).value }}
                placeholder="-XX:+UseG1GC"
              />
            </div>
            <div class={styles.formGroup}>
              <label class={styles.formLabel}>Game Arguments</label>
              <textarea
                class={styles.textarea}
                value={form.game_args}
                onInput={(e) => { form.game_args = (e.target as HTMLTextAreaElement).value }}
                placeholder="--demo"
              />
            </div>
          </div>
        </div>

        <div class={styles.section}>
          <div class={styles.sectionTitle}>Download Source</div>
          <div class={styles.radioGroup}>
            <label
              class={styles.radioLabel}
              onClick={() => { form.download_source = 'mojang' }}
            >
              <span class={[styles.radioDot, form.download_source === 'mojang' && styles.radioDotActive].filter(Boolean).join(' ')} />
              Mojang
            </label>
            <label
              class={styles.radioLabel}
              onClick={() => { form.download_source = 'bmclapi' }}
            >
              <span class={[styles.radioDot, form.download_source === 'bmclapi' && styles.radioDotActive].filter(Boolean).join(' ')} />
              BMCLAPI
            </label>
          </div>
        </div>

        <div class={styles.section}>
          <div class={styles.sectionTitle}>Language</div>
          <select
            class={styles.select}
            value={form.language}
            onChange={(e) => { form.language = (e.target as HTMLSelectElement).value }}
            style={{ width: '200px' }}
          >
            <option value="zh-CN">简体中文</option>
            <option value="en-US">English</option>
          </select>
        </div>

        <div class={styles.btnRow}>
          <button class={[styles.btn, styles.btnSave].join(' ')} onClick={handleSave}>
            Save Settings
          </button>
          {saved.value && <span class={styles.savedText}>Saved!</span>}
        </div>
      </div>
    )
  },
})
