/*
 * OAuth 콜백 착지 (API-002 실연동용)
 * 실서버 흐름: 프론트 → /oauth2/authorization/{github|kakao} → 소셜 동의
 *   → 백엔드 콜백(/login/oauth2/code/*)에서 JWT 발급
 *   → 백엔드가 여기(/oauth/callback?token=...&isNew=true|false)로 리다이렉트.
 * 여기서 토큰을 저장하고 프로필(API-009)을 받아 세션을 연다.
 * 목 모드에선 로그인/가입 화면이 리다이렉트 없이 바로 signIn 하므로 이 페이지는 실서버 전용.
 */
import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import * as api from '../../api/client';
import { useAuth } from '../../context/AuthContext';

export default function OAuthCallback() {
  const { signIn } = useAuth();
  const nav = useNavigate();
  const [params] = useSearchParams();
  const [err, setErr] = useState('');

  useEffect(() => {
    const token = params.get('token');
    if (!token) { setErr('토큰이 없어요. 로그인부터 다시 시도해주세요.'); return; }
    // 프로필 조회에 JWT가 필요하니 먼저 저장 → 받아온 유저로 세션 확정
    localStorage.setItem('cogi-jwt', token);
    api.getProfile()
      .then((user) => {
        signIn(token, user);
        nav(params.get('isNew') === 'true' || !user.onboardingCompleted ? '/onboarding' : '/app', { replace: true });
      })
      .catch(() => setErr('프로필을 불러오지 못했어요. 잠시 후 다시 로그인해주세요.'));
  }, []); // eslint-disable-line react-hooks/exhaustive-deps — 최초 1회만

  return (
    <main className="auth-wrap">
      <div className="auth-card tc">
        {err ? (
          <>
            <h2>로그인 실패</h2>
            <p className="desc">{err}</p>
            <Link className="btn co" to="/login">로그인으로 돌아가기</Link>
          </>
        ) : (
          <>
            <h2>코기가 문 열어주는 중…</h2>
            <p className="desc">소셜 로그인을 확인하고 있어요. 곧 이동합니다.</p>
          </>
        )}
      </div>
    </main>
  );
}
