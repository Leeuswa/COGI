import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// 특별한 설정 없음. 백엔드(Spring Boot :8080) 붙일 때 proxy 주석만 풀면 된다.
export default defineConfig({
  plugins: [react()],
  // server: {
  //   proxy: { '/api': 'http://localhost:8080', '/oauth2': 'http://localhost:8080' },
  // },
});
