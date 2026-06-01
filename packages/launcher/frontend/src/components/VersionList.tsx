import { defineComponent, type PropType } from 'vue'

import type { VersionInfo, Loader } from '@/types'

import styles from './VersionList.module.scss'

export default defineComponent({
  props: {
    versions: {
      type: Array as PropType<VersionInfo[]>,
      required: true,
    },
  },
  setup(props) {
    const loaderIcons: Record<Loader, string> = {
      forge: '🔥',
      neoforge: '⚡',
      fabric: '🧵',
    }

    return () => (
      <div class={styles.versionList}>
        {props.versions.map((v) => (
          <div class={styles.versionCard} key={v.mc_version}>
            <div class={styles.versionHeader}>
              <span class={styles.mcVersion}>{v.mc_version}</span>
              <span class={styles.javaBadge}>JDK {v.java}</span>
            </div>
            <div class={styles.loaders}>
              {v.loaders?.map((loader: Loader) => (
                <span class={styles.loaderTag} key={loader}>
                  {loaderIcons[loader]} {loader}
                </span>
              ))}
            </div>
            <div class={styles.forgeVersion}>{v.forge}</div>
          </div>
        ))}
      </div>
    )
  },
})
