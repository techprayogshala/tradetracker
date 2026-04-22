import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    host: '0.0.0.0',   // Needed for Docker
    port: 5173,
    proxy: {
      // Proxy /api to the Spring Boot backend in dev
      '/api': {
        target: 'http://backend:8080',
        changeOrigin: true,
      },
    },
  },
})
