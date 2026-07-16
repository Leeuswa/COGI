/*
 * [마이페이지 탭] 프로필 (AUTH-006, API-010, FR-11~12)
 * 계정 정보(닉네임/이메일) + 코딩 수준 + 관심기술.
 * 잠금 규칙: 소셜(카카오/GitHub) 가입자는 이름·이메일 둘 다 잠금(소셜 소유 정보),
 *           이메일 가입자는 닉네임만 수정 가능(이메일은 로그인 ID).
 */
import InterestPicker from '../../../components/InterestPicker';

const LEVELS = [['BEGINNER', '초급'], ['INTERMEDIATE', '중급'], ['ADVANCED', '고급']];

export default function ProfileTab({
  user, isSocial, providerName,
  name, setName, level, setLevel, interests, toggle,
  onSave, busy,
  pw, setPw, pwOpen, setPwOpen, onChangePw, pwBusy,
}) {
  return (
    <div className="panel">
      <h3>계정 정보</h3>
      <div className="form" style={{ maxWidth: 420, marginBottom: 26 }}>
        <div>
          <label>이름(닉네임){isSocial && ` — ${providerName} 제공, 수정 불가`}</label>
          <input type="text" value={name} maxLength={20} disabled={isSocial}
            onChange={(e) => setName(e.target.value)} />
        </div>
        <div>
          <label>이메일{isSocial ? ` — ${providerName} 제공, 수정 불가` : ' — 로그인 ID라 변경 불가'}</label>
          <input type="email" value={user.email} disabled />
        </div>
        {isSocial && (
          <p className="note sm" style={{ lineHeight: 1.8 }}>
            {providerName} 계정에서 가져온 정보예요. 바꾸려면 {providerName} 쪽에서 수정하면
            다음 로그인 때 자동으로 따라옵니다.
          </p>
        )}
      </div>

      <h3>코딩 수준</h3>
      <div className="pick-grid" style={{ marginBottom: 22 }}>
        {LEVELS.map(([code, label]) => (
          <button key={code} className={`pick ${level === code ? 'on' : ''}`} onClick={() => setLevel(code)}>{label}</button>
        ))}
      </div>
      <h3>관심 기술</h3>
      <div style={{ marginBottom: 22 }}>
        <InterestPicker value={interests} onToggle={toggle} />
      </div>
      {/* 비밀번호 재설정 — 소셜 가입자는 비밀번호가 없으니 숨긴다 */}
      {!isSocial && (
        <>
          <h3>비밀번호 재설정</h3>
          {!pwOpen ? (
            <button className="btn wh sm" onClick={() => setPwOpen(true)}>비밀번호 재설정</button>
          ) : (
            <div className="form" style={{ maxWidth: 360 }}>
              <div>
                <label>현재 비밀번호</label>
                <input type="password" value={pw.cur} autoComplete="current-password"
                  onChange={(e) => setPw((v) => ({ ...v, cur: e.target.value }))} />
              </div>
              <div>
                <label>새 비밀번호 (8자 이상)</label>
                <input type="password" value={pw.next} autoComplete="new-password"
                  onChange={(e) => setPw((v) => ({ ...v, next: e.target.value }))} />
              </div>
              <div>
                <label>새 비밀번호 확인</label>
                <input type="password" value={pw.confirm} autoComplete="new-password"
                  onChange={(e) => setPw((v) => ({ ...v, confirm: e.target.value }))} />
              </div>
              <div className="row">
                <button className="btn co sm" onClick={onChangePw} disabled={pwBusy}>
                  {pwBusy ? '변경 중…' : '변경하기'}
                </button>
                <button className="btn wh sm" onClick={() => setPwOpen(false)}>취소</button>
              </div>
            </div>
          )}
        </>
      )}

      {/* 프로필 저장 — 페이지 최하단 오른쪽 (수정 흐름의 마지막 동작) */}
      <div className="row" style={{ marginTop: 28 }}>
        <span className="ml-auto" />
        <button className="btn co" onClick={onSave} disabled={busy}>{busy ? '저장 중…' : '프로필 저장'}</button>
      </div>
    </div>
  );
}
