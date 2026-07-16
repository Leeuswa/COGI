import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import { AuthProvider } from './context/AuthContext';
import { GameProvider } from './context/GameContext';
import './styles/base.css';       // 토큰·리셋·네비 (항상 먼저)
import './styles/landing.css';    // 랜딩 섹션 + 다마고치 기기
import './styles/app.css';        // 앱 내부 화면 공통
import './styles/responsive.css'; // 브레이크포인트 (항상 마지막)

// GameProvider가 AuthProvider 안쪽인 이유: 크레딧 한도가 유저 플랜을 봐야 해서
ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <GameProvider>
          <App />
        </GameProvider>
      </AuthProvider>
    </BrowserRouter>
  </React.StrictMode>
);
