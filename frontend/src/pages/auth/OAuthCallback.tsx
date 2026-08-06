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
    // JWT는 백엔드가 이미 HttpOnly 쿠키로 심어놨다(URL에 토큰 없음). 쿠키로 프로필을 받아 세션을 연다.
    const isNew = params.get('isNew') === 'true';
    api.getProfile()
      .then((user) => {
        signIn(user);
        nav(isNew || !user.onboardingCompleted ? '/onboarding' : '/app', { replace: true });
      })
      .catch(() => setErr('로그인 처리에 실패했어요. 잠시 후 다시 시도해주세요.'));
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
