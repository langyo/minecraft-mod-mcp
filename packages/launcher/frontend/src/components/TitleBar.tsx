import { defineComponent, computed, watch } from 'vue'
import { getCurrentWindow } from '@tauri-apps/api/window'
import { Minus, Square, X, Languages } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'

import Dropdown from '@/components/Dropdown'
import type { DropdownOption } from '@/components/Dropdown'
import { useLauncherStore } from '@/stores'
import { saveConfig } from '@/api/config'
import type { Language } from '@/types'

import styles from './TitleBar.module.scss'

const langOptions: DropdownOption[] = [
  { value: 'zh-CN', label: '简体中文', icon: Languages },
  { value: 'en-US', label: 'English', icon: Languages },
]

export default defineComponent({
  setup() {
    const { t, locale } = useI18n()
    const store = useLauncherStore()
    const appWindow = getCurrentWindow()

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
        <div class={styles.titlebarCenter}>
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
            class={[styles.titlebarBtn, styles.minimize].join(' ')}
            onClick={minimize}
            title={t('titlebar.minimize')}
          >
            <Minus size={10} />
          </button>
          <button
            class={[styles.titlebarBtn, styles.maximize].join(' ')}
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
