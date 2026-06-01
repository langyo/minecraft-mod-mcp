import { defineComponent, type PropType } from 'vue'

import { Flame, Zap, Box } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'

import type { VersionInfo, Loader } from '@/types'
import styles from './VersionItem.module.scss'
import { getLoaders } from '@/types'

const loaderIcons: Record<Loader, typeof Flame> = {
  forge: Flame,
  neoforge: Zap,
  fabric: Box,
}

const loaderColors: Record<Loader, string> = {
  forge: '#f7768e',
  neoforge: '#e0af68',
  fabric: '#7dcfff',
}

const loaderKeys: Record<Loader, string> = {
  forge: 'loaders.forge',
  neoforge: 'loaders.neoforge',
  fabric: 'loaders.fabric',
}

export default defineComponent({
  props: {
    version: {
      type: Object as PropType<VersionInfo>,
      required: true,
    },
    active: {
      type: Boolean,
      default: false,
    },
  },
  emits: ['select'],
  setup(props, { emit }) {
    const { t } = useI18n()

    function handleClick() {
      emit('select', props.version)
    }

    return () => (
      <div
        class={[styles.item, props.active && styles.active].filter(Boolean).join(' ')}
        onClick={handleClick}
      >
        <div class={styles.itemHeader}>
          <span class={styles.mcVersion}>{props.version.mc_version}</span>
          <span class={styles.javaBadge}>{t('common.jdk')} {props.version.java}</span>
        </div>
        <div class={styles.loaders}>
          {getLoaders(props.version).map((loader: Loader) => (
            <span
              class={styles.loaderTag}
              key={loader}
              style={{ color: loaderColors[loader] }}
            >
              {(() => { const Icon = loaderIcons[loader]; return <Icon size={12} color={loaderColors[loader]} /> })()} {t(loaderKeys[loader])}
            </span>
          ))}
        </div>
        {props.version.neoforge && (
          <div class={styles.forgeVer}>
            {t('versionItem.neoforge')} {props.version.neoforge}
          </div>
        )}
      </div>
    )
  },
})
