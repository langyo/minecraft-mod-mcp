import { onMounted, onUnmounted, type Ref } from 'vue'

export function useClickOutside(
  el: Ref<HTMLElement | null>,
  handler: () => void,
) {
  function onClick(e: MouseEvent) {
    if (el.value && !el.value.contains(e.target as Node)) {
      handler()
    }
  }

  onMounted(() => document.addEventListener('mousedown', onClick))
  onUnmounted(() => document.removeEventListener('mousedown', onClick))
}
