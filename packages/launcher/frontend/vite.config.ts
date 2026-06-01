import { defineConfig } from 'vite'

import vueJsx from '@vitejs/plugin-vue-jsx'
import { resolve } from 'path'

const host = process.env.TAURI_DEV_HOST

export default defineConfig({
  plugins: [vueJsx()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
      '@i18n': resolve(__dirname, '../../shared/i18n'),
    },
  },
  clearScreen: false,
  server: {
    port: 5173,
    strictPort: true,
    host: host || false,
    fs: {
      allow: ['..', resolve(__dirname, '../../shared')],
    },
    hmr: host
      ? {
          protocol: 'ws',
          host,
          port: 5174,
        }
      : undefined,
    watch: {
      ignored: ['**/backend/**'],
    },
  },
})
