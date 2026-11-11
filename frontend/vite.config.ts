import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '127.0.0.1', port: Number(process.env.APARTMENT_FRONTEND_PORT ?? 5174), strictPort: true,
    proxy: { '/api': { target: `http://127.0.0.1:${process.env.APARTMENT_BACKEND_PORT ?? 8081}` } },
  },
  preview: { host: '127.0.0.1', port: 4174, strictPort: true },
})
