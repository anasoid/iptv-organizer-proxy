import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  base: process.env.VITE_BASE_PATH || '/',
  define: {
    __BASE_PATH__: JSON.stringify(
      process.env.VITE_BASE_PATH || '/'
    ),
    __API_BASE_URL__: JSON.stringify(
      process.env.VITE_API_BASE_URL || ''
    ),
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
