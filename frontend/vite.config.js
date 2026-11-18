import vue from '@vitejs/plugin-vue2'
import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'API_')
  return {
    plugins: [vue()],
    css: {
      preprocessorOptions: { sass: { silenceDeprecations: ['slash-div'] } },
    },
    server: {
      proxy: {
        '/api': env.API_TARGET || 'http://127.0.0.1:8080',
      },
    },
  }
})
