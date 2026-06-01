import { defineComponent, ref, type PropType } from 'vue'

import styles from './Dropdown.module.scss'
import { useClickOutside } from '@/composables/useClickOutside'

export interface DropdownOption {
  value: string
  label: string
  icon?: any
}

export default defineComponent({
  props: {
    options: { type: Array as PropType<DropdownOption[]>, required: true },
    modelValue: { type: String, required: true },
    compact: { type: Boolean, default: false },
    align: { type: String as PropType<'left' | 'right'>, default: 'left' },
  },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    const root = ref<HTMLElement | null>(null)
    const open = ref(false)

    useClickOutside(root, () => { open.value = false })

    function select(val: string) {
      emit('update:modelValue', val)
      open.value = false
    }

    const current = () => props.options.find((o) => o.value === props.modelValue)

    return () => (
      <div ref={root} class={styles.dropdown}>
        <button
          class={[styles.trigger, props.compact && styles.compact].filter(Boolean).join(' ')}
          onClick={(e) => {
            e.stopPropagation()
            open.value = !open.value
          }}
        >
          <span class={styles.triggerLabel}>{current()?.label ?? props.modelValue}</span>
          <svg class={[styles.chevron, open.value && styles.chevronOpen].filter(Boolean).join(' ')} width="10" height="6" viewBox="0 0 10 6">
            <path d="M1 1L5 5L9 1" stroke="currentColor" stroke-width="1.5" fill="none" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </button>
        {open.value && (
          <div class={[styles.menu, props.align === 'right' && styles.menuRight].filter(Boolean).join(' ')}>
            {props.options.map((opt) => (
              <button
                key={opt.value}
                class={[styles.item, opt.value === props.modelValue && styles.itemActive].filter(Boolean).join(' ')}
                onClick={(e) => {
                  e.stopPropagation()
                  select(opt.value)
                }}
              >
                {opt.icon && <span class={styles.itemIcon}>{<opt.icon size={14} />}</span>}
                <span>{opt.label}</span>
              </button>
            ))}
          </div>
        )}
      </div>
    )
  },
})
