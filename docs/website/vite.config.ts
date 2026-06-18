import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import UnoCSS from 'unocss/vite'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [
    vue(),
    UnoCSS(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      '@res': fileURLToPath(new URL('./res', import.meta.url)),
    },
  },
  server: {
    port: 4173,
  },
  build: {
    outDir: 'dist',
  },
})
