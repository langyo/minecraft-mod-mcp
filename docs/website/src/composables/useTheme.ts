import { ref, watch, onMounted } from 'vue'

type Theme = 'dark' | 'light'

const theme = ref<Theme>('dark')
const listeners: Array<(t: Theme) => void> = []

export function useTheme() {
  const isDark = () => typeof window !== 'undefined' && document.documentElement.classList.contains('dark')

  function apply(t: Theme) {
    document.documentElement.classList.toggle('dark', t === 'dark')
    document.documentElement.setAttribute('data-theme', t)
    localStorage.setItem('mmmcp-theme', t)
  }

  function setTheme(t: Theme) {
    theme.value = t
    apply(t)
    listeners.forEach(fn => fn(t))
  }

  function toggleTheme() {
    setTheme(theme.value === 'dark' ? 'light' : 'dark')
  }

  function onChange(fn: (t: Theme) => void) {
    listeners.push(fn)
  }

  onMounted(() => {
    const stored = localStorage.getItem('mmmcp-theme') as Theme | null
    if (stored) {
      theme.value = stored
      apply(stored)
    } else {
      apply('dark')
    }
  })

  return { theme, isDark, setTheme, toggleTheme, onChange }
}
