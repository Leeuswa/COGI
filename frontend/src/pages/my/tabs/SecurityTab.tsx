/*
 * [마이페이지 탭] TOTP 2차 인증 (AUTH-003-OTP, API-006-1, FR-06-1)
 * 이메일 로그인 전용. 설정 → 시크릿/QR 노출 → 인증앱 6자리 검증 통과해야 실제 활성화.
 */
import { useState } from 'react';

export default function SecurityTab({ user, totp, onSetup, onEnable, busy }) {
  const [code, setCode] = useState('');

  return (
    <div className="panel">
      <h3>TOTP 2차 인증</h3>
      <p className="note" style={{ lineHeight: 2, marginBottom: 16 }}>
        이메일 로그인에만 적용돼요. (GitHub/카카오 로그인은 소셜 쪽 보안을 따릅니다)
      </p>

      {user.totpEnabled ? (
        <p style={{ fontSize: 13.5 }}><span className="chip low">활성</span> 2차 인증이 켜져 있어요.</p>
      ) : totp ? (
        // setup 완료, 아직 미활성 — 앱 등록 후 코드로 확정
        <>
          <p style={{ fontSize: 13.5, marginBottom: 10 }}>인증 앱(Google Authenticator 등)에 등록하세요.</p>
          {totp.qrCodeUrl && (
            <img src={totp.qrCodeUrl} alt="OTP QR" width={168} height={168}
              style={{ border: '3px solid var(--navy)', imageRendering: 'auto', marginBottom: 10 }} />
          )}
          <p className="note sm">QR을 못 읽으면 아래 시크릿을 수동 입력:</p>
          <pre className="codebox" style={{ fontSize: 14 }}>{totp.secret}</pre>
          <div style={{ display: 'flex', gap: 8, marginTop: 14, maxWidth: 320 }}>
            <input value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
              placeholder="앱에 뜬 6자리" inputMode="numeric" maxLength={6}
              style={{ flex: 1, padding: '10px 12px', border: '3px solid var(--navy)', fontSize: 15, letterSpacing: 2 }} />
            <button className="btn co sm" onClick={() => onEnable(code)} disabled={busy || code.length !== 6}>
              {busy ? '확인 중…' : '활성화'}
            </button>
          </div>
        </>
      ) : (
        <button className="btn co" onClick={onSetup} disabled={busy}>
          {busy ? '설정 중…' : 'OTP 설정하기'}
        </button>
      )}
    </div>
  );
}
