/*
 * 로그인 세션 관리.
 * JWT는 HttpOnly 쿠키(서버 관리, JS 접근 불가)에 두고, 화면 표시용 user 정보만 localStorage('cogi-user')에 캐싱한다.
 * localStorage에 토큰을 두지 않는 이유: XSS로 토큰이 새는 걸 막기 위해(쿠키+HttpOnly).
 * 새로고침 시 user는 API-009(getProfile)로 다시 받아오면 된다. (쿠키가 살아 있으면 인증 유지)
 */
import { createContext, useContext, useState } from 'react';
import { claimGuestReview, logout as apiLogout } from '../api/client';

const AuthCtx = createContext(null);

// 세션(JWT) 수명 — 백엔드 jwt.access-token-expiration(1시간)과 맞춤. 헤더 타이머·자동 로그아웃 기준.
export const SESSION_MS = 2 * 60 * 60 * 1000;

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    try { return JSON.parse(localStorage.getItem('cogi-user') || 'null'); }
    catch { return null; }
  });

  // 로그인 성공(이메일/GitHub/카카오 공통). 토큰은 이미 쿠키에 있으니 user 정보만 캐싱한다.
  const signIn = (userData) => {
    localStorage.setItem('cogi-user', JSON.stringify(userData));
    localStorage.setItem('cogi-expires', String(Date.now() + SESSION_MS)); // 세션 만료 예정 시각
    setUser(userData);
    // 게스트 체험 리뷰가 있으면 내 계정으로 매핑.
    // 모든 로그인/가입 경로가 여길 지나므로 이 한 곳이면 충분. 실패해도 로그인은 막지 않는다.
    const guest = localStorage.getItem('cogi-guest-review');
    if (guest) {
      try {
        const { reviewId, guestToken } = JSON.parse(guest);
        claimGuestReview(reviewId, guestToken).catch(() => {});
      } catch { /* 파싱 실패 = 버린다 */ }
      localStorage.removeItem('cogi-guest-review');
    }
  };

  // 프로필 일부만 갱신 (온보딩 제출, GitHub 연동, 플랜 변경 등에서 씀)
  const patchUser = (patch) => {
    setUser((prev) => {
      const next = { ...prev, ...patch };
      localStorage.setItem('cogi-user', JSON.stringify(next));
      return next;
    });
  };

  // 로그아웃 — HttpOnly 쿠키는 JS로 못 지우므로 서버에 삭제를 요청한다. (실패해도 로컬 상태는 비운다)
  const signOut = async () => {
    try { await apiLogout(); } catch { /* 네트워크 실패해도 클라이언트는 로그아웃 처리 */ }
    localStorage.removeItem('cogi-user');
    localStorage.removeItem('cogi-expires');
    setUser(null);
  };

  return (
    <AuthCtx.Provider value={{ user, signIn, signOut, patchUser }}>
      {children}
    </AuthCtx.Provider>
  );
}

export const useAuth = () => useContext(AuthCtx);
