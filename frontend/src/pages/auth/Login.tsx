/*
 * 로그인 (AUTH-004, API-006)
 * - 이메일 로그인 + GitHub / 카카오 소셜 버튼.
 * - 소셜은 백엔드 OAuth 엔드포인트로 "이동"하는 방식이라 목 모드에선
 *   바로 로그인된 걸로 처리한다. (실서버에선 window.location 으로 리다이렉트)
 * - totpRequired 가 내려오면 /otp 로 넘긴다. (OTP는 이메일 로그인만 — 정책 확정본)
 * - 5회 실패 잠금(423)은 백엔드 소관. 프론트는 메시지만 보여준다.
 */
import { useState } from 'react';
import { Link, useNavigate, useLocation, useSearchParams } from 'react-router-dom';
import * as api from '../../api/client';
import { useAuth } from '../../context/AuthContext';

export default function Login() {
  const { signIn } = useAuth();
  const nav = useNavigate();
  const st = useLocation().state;
  const from = st?.from || '/app';
  // 가입/비번재설정 완료 후 넘어온 안내 (한 번만 캡처)
  const [notice] = useState(
    st?.notice || (st?.signupDone ? '회원가입이 완료됐어요. 새 계정으로 로그인해주세요.' : '')
  );

  const [params] = useSearchParams();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  // 소셜 로그인 실패로 ?error=... 달고 돌아온 경우 안내
  const [err, setErr] = useState(() => {
    if (params.get('expired')) return '세션이 만료됐어요. 다시 로그인해주세요.';
    const e = params.get('error');
    if (e === 'email_exists') return '이미 존재하는 이메일입니다.';
    return e ? '소셜 로그인에 실패했어요. 다시 시도해주세요.' : '';
  });
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    if (busy) return;
    setErr('');
    setBusy(true);
    try {
      // 로그인 성공 시 JWT는 HttpOnly 쿠키로 세팅됨(응답 body엔 토큰 없음).
      const res = await api.login(email, password);
      if (res?.totpRequired) {
        // OTP 활성 계정은 2차 인증으로. 최종 JWT는 OTP 통과 후 발급 (TOTP 구현 시 사용)
        nav('/otp', { state: { tempToken: res.tempToken, from } });
        return;
      }
      // 쿠키로 인증된 상태에서 내 프로필을 받아 세션을 연다.
      const user = await api.getProfile();
      signIn(user);
      nav(user.onboardingCompleted ? from : '/onboarding');
    } catch (ex) {
      setErr(ex.status === 423
        ? '5회 이상 실패로 계정이 잠겼어요. 비밀번호 찾기로 잠금을 풀 수 있습니다.'
        : '아이디 또는 비밀번호가 맞지 않아요.\n(테스트 계정: team · member · user · admin / 비밀번호 1234)');
    } finally {
      setBusy(false);
    }
  };

  // 소셜 로그인 — 백엔드 OAuth 진입점으로 풀페이지 이동. 동의 후 백엔드가 쿠키를 세팅하고 /oauth/callback 으로 돌려보낸다.
  const social = (provider) => {
    window.location.href = provider === 'github' ? api.githubOAuthUrl() : api.kakaoOAuthUrl();
  };

  return (
    <main className="auth-wrap">
      <div className="auth-card">
        <h2>다시 만나서 반가워요</h2>
        <p className="desc">코기가 기다리고 있었어요. 밥 줄 시간입니다.</p>
        {notice && <p className="desc" style={{ color: '#1a7f37', fontWeight: 700 }}>{notice}</p>}

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
