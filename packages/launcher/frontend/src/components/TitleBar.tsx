import { defineComponent, computed } from 'vue'

import { getCurrentWindow } from '@tauri-apps/api/window'
import { Minus, Square, X, Sun, Moon, Monitor } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'

import type { DropdownOption } from '@/components/Dropdown'
import type { Language, Theme } from '@/types'
import styles from './TitleBar.module.scss'
import { saveConfig } from '@/api/config'
import Dropdown from '@/components/Dropdown'
import { useLauncherStore } from '@/stores'

const langOptions: DropdownOption[] = [
  { value: 'zh-CN', label: '简体中文' },
  { value: 'zh-TW', label: '繁體中文' },
  { value: 'en-US', label: 'English' },
  { value: 'ja-JP', label: '日本語' },
  { value: 'ko-KR', label: '한국어' },
  { value: 'de-DE', label: 'Deutsch' },
  { value: 'fr-FR', label: 'Français' },
  { value: 'es-ES', label: 'Español' },
]

function getSystemDark(): boolean {
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

function applyTheme(isDark: boolean) {
  document.documentElement.classList.toggle('light', !isDark)
}

export default defineComponent({
  setup() {
    const { t, locale } = useI18n()
    const store = useLauncherStore()
    const appWindow = getCurrentWindow()

    const followSystem = computed(() => store.config?.follow_system_theme !== false)
    const isDark = computed(() => {
      if (followSystem.value) return getSystemDark()
      return store.config?.theme !== 'light'
    })

    const currentLang = computed(() => store.config?.language ?? 'zh-CN')

    async function handleLangChange(lang: string) {
      locale.value = lang
      if (store.config) {
        const updated = { ...store.config, language: lang as Language }
        try {
          await saveConfig(updated)
          await store.fetchConfig()
        } catch {}
      }
    }

    async function toggleTheme() {
      if (!store.config) return
      const newDark = !isDark.value
      const newTheme: Theme = newDark ? 'dark' : 'light'
      applyTheme(newDark)
      const updated = {
        ...store.config,
        theme: newTheme,
        follow_system_theme: false as boolean,
      }
      try {
        await saveConfig(updated)
        await store.fetchConfig()
      } catch {}
    }

    async function resetToSystem() {
      if (!store.config) return
      const updated = {
        ...store.config,
        follow_system_theme: true as boolean,
      }
      applyTheme(getSystemDark())
      try {
        await saveConfig(updated)
        await store.fetchConfig()
      } catch {}
    }

    function handleDoubleClick() {
      try { appWindow.toggleMaximize() } catch {}
    }

    function minimize() {
      try { appWindow.minimize() } catch {}
    }

    function toggleMaximize() {
      try { appWindow.toggleMaximize() } catch {}
    }

    function close() {
      try { appWindow.close() } catch {}
    }

    return () => (
      <div
        class={styles.titlebar}
        onDblclick={handleDoubleClick}
      >
        <div class={styles.titlebarLeft}>
          <img class={styles.titlebarLogoImg} src="/logo.webp" alt="MMML" />
          <span class={styles.titlebarTitle}>MMML</span>
        </div>
        <div class={styles.titlebarRight}>
          <button
            class={styles.themeBtn}
            onClick={followSystem.value ? resetToSystem : toggleTheme}
            title={followSystem.value ? t('titlebar.systemMode') : (isDark.value ? t('titlebar.lightMode') : t('titlebar.darkMode'))}
          >
            {followSystem.value ? <Monitor size={14} /> : (isDark.value ? <Sun size={14} /> : <Moon size={14} />)}
          </button>
          <Dropdown
            options={langOptions}
            modelValue={currentLang.value}
            compact
            align="right"
            onUpdate:modelValue={handleLangChange}
          />
        </div>
        <div class={styles.titlebarControls}>
          <button
            class={styles.titlebarBtn}
            onClick={minimize}
            title={t('titlebar.minimize')}
          >
            <Minus size={10} />
          </button>
          <button
            class={styles.titlebarBtn}
            onClick={toggleMaximize}
            title={t('titlebar.maximize')}
          >
            <Square size={10} />
          </button>
          <button
            class={[styles.titlebarBtn, styles.close].join(' ')}
            onClick={close}
            title={t('titlebar.close')}
          >
            <X size={12} />
          </button>
        </div>
      </div>
    )
  },
})
