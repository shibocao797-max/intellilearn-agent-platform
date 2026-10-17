import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiBase = env.VITE_API_BASE || '/dev-api'
  const target = env.VITE_BACKEND_TARGET || 'http://localhost:18081'

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': '/src'
      }
    },
    server: {
      proxy: {
        [apiBase]: {
          target,
          changeOrigin: true,
          rewrite: path => path.replace(new RegExp(`^${apiBase}`), '')
        }
      }
    }
  }
})
