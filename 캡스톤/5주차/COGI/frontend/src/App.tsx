/*
 * 라우팅
 *  /        랜딩
 *  /login   로그인
 *  /signup  회원가입
 * 로그인 후 화면(/app/...)은 아직 없음
 */
import { Routes, Route, Navigate } from 'react-router-dom';
import { Nav, Footer } from './components/ui';
import Landing from './pages/Landing';
import Login from './pages/auth/Login';
import Signup from './pages/auth/Signup';

export default function App() {
  return (
    <div className="app-shell">
      <Nav />
      <div className="app-body">
        <Routes>
          <Route path="/" element={<Landing />} />
          <Route path="/login" element={<Login />} />
          <Route path="/signup" element={<Signup />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </div>
      <Footer />
    </div>
  );
}
