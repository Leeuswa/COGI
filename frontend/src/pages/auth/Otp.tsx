/*
 * OTP 2차 인증 (AUTH-004-1, API-006-2)
 * 이메일 로그인 + TOTP 켠 계정만 여기로 온다. (GitHub/카카오 로그인은 OTP 없음 — 정책 확정본)
 * 로그인 화면에서 state 로 tempToken/user 를 넘겨받고, 6자리 통과 시 최종 JWT 발급.
 * state 없이 직접 URL로 들어오면 로그인으로 돌려보낸다.
 */
import { useState } from 'react';
import { useNavigate, useLocation, Navigate } from 'react-router-dom';
import * as api from '../../api/client';
import { useAuth } from '../../context/AuthContext';

export default function Otp() {
  const { signIn } = useAuth();
  const nav = useNavigate();
  const st = useLocation().state; // { tempToken, user, from }

  const [code, setCode] = useState('');
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  if (!st?.user) return <Navigate to="/login" replace />;

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    try {
      const res = await api.totpVerify(code, st.tempToken);
      signIn(res.accessToken, st.user);
      nav(st.user.onboardingCompleted ? (st.from || '/app') : '/onboarding');
    } catch {
      setErr('코드가 맞지 않아요. 인증 앱의 최신 코드를 확인하세요. (목 모드 힌트: 000000)');
    } finally { setBusy(false); }
  };

  return (
    <main className="auth-wrap">
      <div className="auth-card">
        <h2>2차 인증</h2>
        <p className="desc">인증 앱(Google Authenticator 등)에 표시된 6자리 코드를 입력하세요.</p>
        <form className="form" onSubmit={submit}>
          <div>
            <label>OTP 코드</label>
            <input type="text" inputMode="numeric" maxLength={6} value={code}
              onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
              style={{ fontFamily: 'Silkscreen, monospace', letterSpacing: 6, textAlign: 'center' }}
              autoFocus required />
          </div>
          {err && <p className="err">{err}</p>}
          <button className="btn co" disabled={busy || code.length !== 6}>
            {busy ? '확인 중…' : '인증하기'}
          </button>
        </form>
      </div>
    </main>
  );
}
