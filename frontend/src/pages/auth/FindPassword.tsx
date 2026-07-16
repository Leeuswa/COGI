/*
 * 비밀번호 찾기/재설정 (AUTH-005, API-004/005/007/008)
 * 흐름: 이메일 → 코드 발송 → 코드 검증 → resetToken 발급 → 새 비밀번호 저장.
 * 재설정이 완료되면 로그인 실패 잠금도 함께 풀린다 (명세 비고).
 */
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import * as api from '../../api/client';

export default function FindPassword() {
  const nav = useNavigate();
  const [step, setStep] = useState(1); // 1 이메일 → 2 코드 → 3 새 비밀번호
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [resetToken, setResetToken] = useState(null);
  const [pw, setPw] = useState('');
  const [pw2, setPw2] = useState('');
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  const sendCode = async (e) => {
    e.preventDefault();
    setBusy(true);
    try { await api.sendEmailCode(email, 'RESET_PW'); setStep(2); setErr(''); }
    finally { setBusy(false); }
  };

  const verify = async (e) => {
    e.preventDefault();
    setBusy(true);
    try {
      await api.verifyEmailCode(email, code);
      const res = await api.passwordResetRequest(email, code);
      setResetToken(res.resetToken);
      setStep(3);
      setErr('');
    } catch { setErr('인증코드가 맞지 않아요. (목 모드 힌트: 000000)'); }
    finally { setBusy(false); }
  };

  const save = async (e) => {
    e.preventDefault();
    if (pw.length < 8) return setErr('비밀번호는 8자 이상이어야 해요.');
    if (pw !== pw2) return setErr('비밀번호 확인이 일치하지 않아요.');
    setBusy(true);
    try {
      await api.passwordReset(resetToken, pw);
      nav('/login');
    } finally { setBusy(false); }
  };

  return (
    <main className="auth-wrap">
      <div className="auth-card">
        <h2>비밀번호 찾기</h2>
        <p className="desc">
          {step === 1 && '가입한 이메일로 인증코드를 보내드려요.'}
          {step === 2 && `${email} 로 보낸 6자리 코드를 입력하세요.`}
          {step === 3 && '새 비밀번호를 정해주세요. 잠긴 계정도 함께 풀립니다.'}
        </p>

        {step === 1 && (
          <form className="form" onSubmit={sendCode}>
            <div>
              <label>이메일</label>
              <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
            </div>
            <button className="btn co" disabled={busy}>{busy ? '발송 중…' : '인증코드 받기'}</button>
          </form>
        )}

        {step === 2 && (
          <form className="form" onSubmit={verify}>
            <div>
              <label>인증코드 6자리</label>
              <input type="text" inputMode="numeric" maxLength={6} value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
                style={{ fontFamily: 'Silkscreen, monospace', letterSpacing: 6, textAlign: 'center' }} required />
            </div>
            {err && <p className="err">{err}</p>}
            <button className="btn co" disabled={busy || code.length !== 6}>확인</button>
          </form>
        )}

        {step === 3 && (
          <form className="form" onSubmit={save}>
            <div>
              <label>새 비밀번호 (8자 이상)</label>
              <input type="password" value={pw} onChange={(e) => setPw(e.target.value)} required />
            </div>
            <div>
              <label>새 비밀번호 확인</label>
              <input type="password" value={pw2} onChange={(e) => setPw2(e.target.value)} required />
            </div>
            {err && <p className="err">{err}</p>}
            <button className="btn co" disabled={busy}>{busy ? '저장 중…' : '비밀번호 변경'}</button>
          </form>
        )}

        <div className="auth-foot"><Link to="/login">로그인으로 돌아가기</Link></div>
      </div>
    </main>
  );
}
