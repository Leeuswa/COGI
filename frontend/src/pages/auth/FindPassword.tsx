/*
 * 비밀번호 찾기/재설정 (AUTH-005, API-004/005/007/008)
 * 흐름: 이메일 → 코드 발송 → 코드 검증 → resetToken 발급 → 새 비밀번호 저장.
 * 재설정이 완료되면 로그인 실패 잠금도 함께 풀린다 (명세 비고).
 */
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import * as api from '../../api/client';
import { useCountdown } from '../../hooks/useCountdown';

// 초 → "m:ss" (예: 125 → "2:05")
const fmt = (s: number) => `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;

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
  const [cooldown, startCooldown] = useCountdown(); // 재발송 쿨타임(초)
  const [expiry, startExpiry] = useCountdown();     // 코드 유효시간(초)

  // 코드 발송 + 두 타이머 시작 (최초 발송·재발송 공용)
  const doSend = async () => {
    setBusy(true);
    try {
      await api.sendEmailCode(email, 'RESET_PW');
      setErr('');
      startCooldown(60);   // 60초 뒤 재발송 가능
      startExpiry(300);    // 코드 5분 유효
      return true;
    } catch {
      setErr('인증코드 발송에 실패했어요. 잠시 후 다시 시도해주세요.');
      return false;
    } finally { setBusy(false); }
  };

  const sendCode = async (e) => {   // step1: 이메일 → 코드 발송
    e.preventDefault();
    if (await doSend()) setStep(2);
  };

  const resend = async () => {      // step2: 재발송 (쿨타임 끝난 뒤에만)
    if (cooldown > 0) return;
    setCode('');
    await doSend();
  };

  const verify = async (e) => {
    e.preventDefault();
    if (expiry === 0) return setErr('코드가 만료됐어요. 다시 받아주세요.');
    setBusy(true);
    try {
      await api.verifyEmailCode(email, code);
      const res = await api.passwordResetRequest(email, code);
      setResetToken(res.resetToken);
      setStep(3);
      setErr('');
    } catch (ex) { setErr(ex.message || '인증코드가 맞지 않아요.'); }
    finally { setBusy(false); }
  };

  const save = async (e) => {
    e.preventDefault();
    if (pw.length < 8) return setErr('비밀번호는 8자 이상이어야 해요.');
    if (pw !== pw2) return setErr('비밀번호 확인이 일치하지 않아요.');
    setBusy(true);
    try {
      await api.passwordReset(resetToken, pw);
      nav('/login', { state: { notice: '비밀번호가 재설정됐어요. 새 비밀번호로 로그인하세요.' } });
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
            {/* 코드 유효시간(5분) 실시간 */}
            <p className="note sm" style={{ marginTop: 4 }}>
              {expiry > 0
                ? <>남은 시간 <b style={{ fontFamily: 'Silkscreen, monospace' }}>{fmt(expiry)}</b></>
                : <span style={{ color: 'crimson' }}>코드가 만료됐어요. 다시 받아주세요.</span>}
            </p>
            {err && <p className="err">{err}</p>}
            <button className="btn co" disabled={busy || code.length !== 6 || expiry === 0}>확인</button>
            {/* 재발송(60초 쿨타임) 실시간 */}
            <button type="button" className="btn wh sm" style={{ marginTop: 8 }}
              onClick={resend} disabled={busy || cooldown > 0}>
              {cooldown > 0 ? `재발송 (${cooldown}초)` : '인증코드 다시 받기'}
            </button>
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
