/*
 * [관리자 탭] 약관 관리 (ADM, API-063 조회 + 관리자 본문 수정)
 * 리뷰 지침 탭과 같은 패턴 — 약관별 본문을 textarea로 편집 후 저장.
 * 버전 올림은 별도(범위 밖) — 여기선 본문(content)만 수정한다.
 */
const TYPE_KO = {
  SERVICE: '서비스',
  PRIVACY: '개인정보',
  PAYMENT: '결제/구독',
  MARKETING: '마케팅',
};

export default function TermsTab({ terms, setTerms, onSave }) {
  return (
    <>
      <div className="panel" style={{ background: '#fffbe8' }}>
        <h3>💡 약관 관리</h3>
        <p style={{ fontSize: 13, lineHeight: 2 }}>
          약관 <b>본문</b>을 수정하면 이후 가입 화면·마이페이지 약관 조회에 반영됩니다.
          버전(v)은 그대로 유지돼요 — 버전 올림이 필요한 큰 개정은 별도로 관리하세요.
        </p>
      </div>
      {terms.map((t) => (
        <div key={t.id} className="panel">
          <h3>
            {t.title}{' '}
            <span className="chip navy">{TYPE_KO[t.type] ?? t.type}</span>{' '}
            <span className="chip gray">v{t.version}</span>{' '}
            <span className={`chip ${t.isRequired ? 'navy' : 'gray'}`}>{t.isRequired ? '필수' : '선택'}</span>
          </h3>
          <div className="form">
            <textarea
              value={t.content}
              style={{ minHeight: 220, resize: 'vertical', fontSize: 13, lineHeight: 1.6 }}
              onChange={(e) =>
                setTerms((ts) => ts.map((x) => (x.id === t.id ? { ...x, content: e.target.value } : x)))
              }
            />
            <button className="btn wh sm" style={{ alignSelf: 'flex-start' }} onClick={() => onSave(t)}>
              저장 (이후 조회부터 반영)
            </button>
          </div>
        </div>
      ))}
    </>
  );
}
