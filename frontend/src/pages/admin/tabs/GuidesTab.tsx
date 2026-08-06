/*
 * [관리자 탭] 수준별 리뷰 지침 (ADM-004, API-021, FR-24~25)
 * "리뷰 지침"이란: AI가 리뷰 코멘트를 쓸 때 따르는 수준별 규칙(말투·설명 깊이·예제 분량).
 * 사용자가 온보딩/프로필에서 고른 코딩 수준(초급/중급/고급)에 따라 다른 지침이 프롬프트에 붙는다.
 * 저장하면 해당 수준 사용자의 "다음" 리뷰부터 적용.
 */
const LEVEL_KO = {
  BEGINNER: ['초급', '용어를 풀어 쓰고, 왜 문제인지부터 차근차근'],
  INTERMEDIATE: ['중급', '핵심만 간결하게, 대안 코드 중심'],
  ADVANCED: ['고급', '성능·설계 트레이드오프까지 심화'],
};

export default function GuidesTab({ guides, setGuides, onSave }) {
  return (
    <>
      <div className="panel" style={{ background: '#fffbe8' }}>
        <h3>💡 리뷰 지침이 뭔가요?</h3>
        <p style={{ fontSize: 13, lineHeight: 2 }}>
          AI가 코드리뷰 코멘트를 쓸 때 따르는 <b>수준별 말투·설명 깊이 규칙</b>이에요.
          사용자가 프로필에서 고른 코딩 수준에 맞는 지침이 리뷰 프롬프트에 자동으로 붙습니다.
          예를 들어 같은 널 체크 이슈라도 초급자에겐 개념부터, 고급자에겐 트레이드오프 중심으로 설명돼요.
        </p>
      </div>
      {Object.entries(guides).map(([level, text]) => (
    <div key={level} className="panel">
      <h3>{LEVEL_KO[level]?.[0] ?? level} <span className="chip navy">{level}</span></h3>
      <p className="note sm" style={{ marginBottom: 12 }}>{LEVEL_KO[level]?.[1]}</p>
      <div className="form">
        {/* 지침이 프롬프트 전체라 길다 — 충분히 크게 + 세로 리사이즈 허용 */}
        <textarea value={text as string}
          style={{ minHeight: 320, resize: 'vertical', fontSize: 13, lineHeight: 1.6 }}
          onChange={(e) => setGuides((g) => ({ ...g, [level]: e.target.value }))} />
        <button className="btn wh sm" style={{ alignSelf: 'flex-start' }} onClick={() => onSave(level)}>
          저장 (다음 리뷰부터 반영)
        </button>
      </div>
    </div>
      ))}
    </>
  );
}
