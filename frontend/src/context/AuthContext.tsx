/*
 * 로그인 세션 관리.
 * JWT는 localStorage('cogi-jwt'), 유저 정보는 localStorage('cogi-user')에 둔다.
 * 세션 스토리지가 아니라 로컬인 이유: 다마고치 특성상 자주 들락거리는데
 * 매번 로그인시키면 리텐션 서비스가 리텐션을 깎아먹는 꼴이라서.
 * 백엔드 붙으면 user는 API-009(getProfile)로 새로고침 시 다시 받아오면 된다.
 */
import { createContext, useContext, useState } from 'react';
import { claimGuestReview } from '../api/client';

const AuthCtx = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    try { return JSON.parse(localStorage.getItem('cogi-user') || 'null'); }
    catch { return null; }
  });

  // 로그인 성공(이메일/GitHub/카카오 공통). token + user를 한 번에 저장
  const signIn = (token, userData) => {
    localStorage.setItem('cogi-jwt', token);
    localStorage.setItem('cogi-user', JSON.stringify(userData));
    setUser(userData);
    // 게스트 체험 리뷰가 있으면 내 계정으로 매핑 (API-039-1).
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

  const signOut = () => {
    localStorage.removeItem('cogi-jwt');
    localStorage.removeItem('cogi-user');
    setUser(null);
  };

  return (
    <AuthCtx.Provider value={{ user, signIn, signOut, patchUser }}>
      {children}
    </AuthCtx.Provider>
  );
}

export const useAuth = () => useContext(AuthCtx);
