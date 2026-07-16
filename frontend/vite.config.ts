import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    // 프론트(:5173) 요청을 백엔드(:8080)로 전달 (CORS/쿠키 편해짐)
    proxy: {
      '/api': 'http://localhost:8080',
      '/oauth2': 'http://localhost:8080',
      '/login': 'http://localhost:8080',
    },
  },
});