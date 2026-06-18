import { defineConfig, presetWind, presetIcons, presetTypography } from 'unocss'

export default defineConfig({
  presets: [
    presetWind(),
    presetTypography(),
    presetIcons(),
  ],
  shortcuts: {
    'btn': 'rounded-xl font-medium transition-all duration-300 cursor-pointer inline-flex items-center justify-center gap-1.5 active:scale-95 select-none',
    'btn-primary': 'btn px-5 py-2.5 bg-gradient-to-r from-emerald-400 to-teal-400 text-slate-900 shadow-lg shadow-emerald-500/20 hover:shadow-xl hover:shadow-emerald-500/35 border-none font-semibold',
    'btn-ghost': 'btn px-5 py-2.5 bg-[var(--bg-glass)] hover:bg-[var(--bg-glass-hover)] backdrop-blur-md border border-[var(--border-subtle)] hover:border-[var(--border-hover)] text-[var(--text-primary)] shadow-sm hover:shadow-md',
    'nav-icon-btn': 'btn w-9 h-9 rounded-xl bg-transparent hover:bg-[var(--bg-glass-hover)] text-[var(--text-secondary)] hover:text-[var(--text-primary)] hover:shadow-md active:scale-95 border-none',
    'nav-lang-btn': 'btn h-9 px-3 rounded-xl text-sm bg-transparent hover:bg-[var(--bg-glass-hover)] text-[var(--text-secondary)] hover:text-[var(--text-primary)] hover:shadow-md active:scale-95 border-none',
    'glass': 'backdrop-blur-xl bg-[var(--bg-glass)] border border-[var(--border-subtle)] rounded-2xl',
    'glass-card': 'glass p-6 transition-all duration-300 ease-out hover:bg-[var(--bg-glass-hover)] hover:border-[var(--border-hover)] hover:shadow-xl hover:shadow-[var(--shadow-card)]',
    'glass-card-static': 'glass p-6 transition-all duration-300 ease-out',
  },
  theme: {
    colors: {
      brand: {
        grass: '#5fd35f',
        cyan: '#2ee6c8',
        teal: '#14b8a6',
        violet: '#8b5cf6',
        amber: '#f5a623',
        red: '#ef4444',
      },
    },
  },
})
