/*
 * 로그인 (AUTH-004, API-006)
 * - 이메일 로그인 + GitHub / 카카오 소셜 버튼.
 * - 소셜은 백엔드 OAuth 엔드포인트로 "이동"하는 방식이라 목 모드에선
 *   바로 로그인된 걸로 처리한다. (실서버에선 window.location 으로 리다이렉트)
 * - totpRequired 가 내려오면 /otp 로 넘긴다. (OTP는 이메일 로그인만 — 정책 확정본)
 * - 5회 실패 잠금(423)은 백엔드 소관. 프론트는 메시지만 보여준다.
 */
import { useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import * as api from '../../api/client';
import { useAuth } from '../../context/AuthContext';

export default function Login() {
  const { signIn } = useAuth();
  const nav = useNavigate();
  const from = useLocation().state?.from || '/app';

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    if (busy) return;
    setErr('');
    setBusy(true);
    try {
      const res = await api.login(email, password);
      if (res.totpRequired) {
        // 2차 인증으로. JWT는 OTP 통과 후에 받는다
        nav('/otp', { state: { tempToken: res.tempToken, user: res.user, from } });
        return;
      }
      signIn(res.accessToken, res.user);
      nav(res.user.onboardingCompleted ? from : '/onboarding');
    } catch (ex) {
      setErr(ex.status === 423
        ? '5회 이상 실패로 계정이 잠겼어요. 비밀번호 찾기로 잠금을 풀 수 있습니다.'
        : '아이디 또는 비밀번호가 맞지 않아요.\n(테스트 계정: team · member · user · admin / 비밀번호 1234)');
    } finally {
      setBusy(false);
    }
  };

  // 소셜 로그인. 실서버: OAuth 리다이렉트(→ /oauth/callback) / 목: client가 만든 계정으로 즉시 로그인
  const social = async (provider) => {
    const url = provider === 'github' ? api.githubOAuthUrl() : api.kakaoOAuthUrl();
    if (url.startsWith('http')) { window.location.href = url; return; }
    const res = await api.socialLogin(provider === 'github' ? 'GITHUB' : 'KAKAO');
    signIn(res.accessToken, res.user); // provider가 실려 있어 마이페이지 이름·이메일 잠금이 걸린다
    nav('/onboarding');
  };

  return (
    <main className="auth-wrap">
      <div className="auth-card">
        <h2>다시 만나서 반가워요</h2>
        <p className="desc">코기가 기다리고 있었어요. 밥 줄 시간입니다.</p>

        <div className="sns">
          <button className="sns-btn github" onClick={() => social('github')}>
            GitHub로 계속하기
          </button>
          <button className="sns-btn kakao" onClick={() => social('kakao')}>
            카카오톡으로 계속하기
          </button>
        </div>

        <div className="divider">OR EMAIL</div>

        <form className="form" onSubmit={submit}>
          <div>
            <label>아이디 (또는 이메일)</label>
            <input type="text" value={email} placeholder="테스트: team · member · user · admin" onChange={(e) => setEmail(e.target.value)} required autoComplete="username" />
          </div>
          <div>
            <label>비밀번호</label>
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required autoComplete="current-password" />
          </div>
          {err && <p className="err">{err}</p>}
          <button className="btn co" type="submit" disabled={busy}>
            {busy ? '확인 중…' : '로그인'}
          </button>
        </form>

        <div className="auth-foot">
          <Link to="/find-password">비밀번호를 잊었어요</Link>
          {' · '}
          <Link to="/signup">회원가입</Link>
        </div>
      </div>
    </main>
  );
}
