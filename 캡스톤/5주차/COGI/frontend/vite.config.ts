import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    // /api 요청은 백엔드(:8080)로 넘김
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
});
