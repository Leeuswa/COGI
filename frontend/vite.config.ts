import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'


//백엔드 연동
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})