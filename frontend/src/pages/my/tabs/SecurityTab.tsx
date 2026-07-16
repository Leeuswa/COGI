/*
 * [마이페이지 탭] TOTP 2차 인증 (AUTH-003-OTP, API-006-1, FR-06-1)
 * 이메일 로그인 전용. 설정하면 시크릿을 보여주고, 다음 로그인부터 6자리 코드를 요구.
 */
export default function SecurityTab({ user, totp, onSetup, busy }) {
  return (
    <div className="panel">
      <h3>TOTP 2차 인증</h3>
      <p className="note" style={{ lineHeight: 2, marginBottom: 16 }}>
        이메일 로그인에만 적용돼요. (GitHub/카카오 로그인은 소셜 쪽 보안을 따릅니다)
      </p>
      {user.totpEnabled && totp ? (
        <>
          <p style={{ fontSize: 13.5, marginBottom: 10 }}><span className="chip low">활성</span> 인증 앱에 아래 시크릿을 등록하세요.</p>
          <pre className="codebox" style={{ fontSize: 14 }}>{totp.secret}</pre>
          <p className="note sm" style={{ marginTop: 10 }}>다음 이메일 로그인부터 6자리 코드를 물어봅니다.</p>
        </>
      ) : user.totpEnabled ? (
        <p style={{ fontSize: 13.5 }}><span className="chip low">활성</span> 2차 인증이 켜져 있어요.</p>
      ) : (
        <button className="btn co" onClick={onSetup} disabled={busy}>
          {busy ? '설정 중…' : 'OTP 설정하기'}
        </button>
      )}
    </div>
  );
}
