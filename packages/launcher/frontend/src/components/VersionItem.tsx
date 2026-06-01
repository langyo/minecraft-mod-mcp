import { defineComponent, type PropType } from 'vue'

import type { VersionInfo, Loader } from '@/types'
import { getLoaders } from '@/types'

import styles from './VersionItem.module.scss'

const loaderIcons: Record<Loader, string> = {
  forge: '\u{1F525}',
  neoforge: '\u{26A1}',
  fabric: '\u{1F9F5}',
}

const loaderColors: Record<Loader, string> = {
  forge: '#f7768e',
  neoforge: '#e0af68',
  fabric: '#7dcfff',
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
          <span class={styles.javaBadge}>JDK {props.version.java}</span>
        </div>
        <div class={styles.loaders}>
          {getLoaders(props.version).map((loader: Loader) => (
            <span
              class={styles.loaderTag}
              key={loader}
              style={{ color: loaderColors[loader] }}
            >
              {loaderIcons[loader]} {loader}
            </span>
          ))}
        </div>
        {props.version.neoforge && (
          <div class={styles.forgeVer}>
            NF {props.version.neoforge}
          </div>
        )}
      </div>
    )
  },
})
