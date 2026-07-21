/*
 * [관리자 탭] AI 사용량 (ADM-001, API-014)
 * 기간 필터 + 비용 합계/상한 경고(FR-81) + 리뷰 응답시간(API-054, FR-77) + 로그 테이블.
 * 데이터 조회는 부모(Admin)가 하고 여긴 그리기만 — 탭 전환 때 재조회를 안 하기 위해서.
 */
export default function UsageTab({ usage, range, setRange, latency }) {
  // 명세(credit_usage 비고): 관리자 전체 비용상한은 두지 않는다 — 합계는 참고용 표기만
  const totalCost = usage.reduce((a, u) => a + Number(u.cost || 0), 0);

  // 일자별 집계 (그래프용) — ai_usage_logs 를 날짜로 묶어 토큰/비용 합산. 벤더 API 없이 우리 로그만으로.
  const byDay = {};
  for (const u of usage) {
    const day = (u.createdAt || '').slice(0, 10);
    if (!day) continue;
    if (!byDay[day]) byDay[day] = { tokens: 0, cost: 0 };
    byDay[day].tokens += (u.inputTokens || 0) + (u.outputTokens || 0);
    byDay[day].cost += Number(u.cost || 0);
  }
  const days = Object.keys(byDay).sort();
  const maxTokens = Math.max(1, ...days.map((d) => byDay[d].tokens));
  const H = 170, barW = 26, gap = 16, padB = 24, padT = 16;

  return (
    <>
      <div className="filter-bar">
        <input type="date" value={range.from} onChange={(e) => setRange((r) => ({ ...r, from: e.target.value }))} />
        <span className="note sm">~</span>
        <input type="date" value={range.to} onChange={(e) => setRange((r) => ({ ...r, to: e.target.value }))} />
        <span className="chip navy ml-auto">기간 합계 ${totalCost.toFixed(4)}</span>
        {latency && (
          <span className={`chip ${latency.p95Sec > latency.targetSec ? 'hi' : 'low'}`} title="리뷰 응답시간 (API-054)">
            응답 평균 {latency.avgSec}s · p95 {latency.p95Sec}s / 목표 {latency.targetSec}s
          </span>
        )}
      </div>
      {/* 일자별 사용량 그래프 — 위 표와 같은 데이터를 날짜로 묶어 시각화 */}
      <div className="panel">
        <div className="row" style={{ justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 10 }}>
          <b>일자별 토큰 사용량</b>
          <span className="note sm">막대에 올리면 그날 비용이 보여요 · 우리 로그(ai_usage_logs) 기준</span>
        </div>
        {days.length === 0 ? (
          <p className="note sm">이 기간에 사용 기록이 없어요.</p>
        ) : (
          <svg viewBox={`0 0 ${Math.max(days.length * (barW + gap), 280)} ${H}`}
               width="100%" height={H} preserveAspectRatio="xMinYMid meet" role="img" aria-label="일자별 토큰 사용량">
            {days.map((d, i) => {
              const { tokens, cost } = byDay[d];
              const barH = Math.round((tokens / maxTokens) * (H - padB - padT));
              const x = i * (barW + gap) + gap / 2;
              const y = (H - padB) - barH;
              return (
                <g key={d}>
                  <title>{`${d}\n토큰 ${tokens.toLocaleString()} · $${cost.toFixed(4)}`}</title>
                  <rect x={x} y={y} width={barW} height={barH} rx="3" fill="#4c6ef5" />
                  <text x={x + barW / 2} y={y - 4} textAnchor="middle" fontSize="9" fill="currentColor">
                    {tokens.toLocaleString()}
                  </text>
                  <text x={x + barW / 2} y={H - 8} textAnchor="middle" fontSize="9" fill="currentColor" opacity="0.6">
                    {d.slice(5)}
                  </text>
                </g>
              );
            })}
          </svg>
        )}
      </div>

      <div className="panel flush">
        <table className="tbl">
          <thead><tr><th>일시</th><th>유저</th><th>모델</th><th>유형</th><th>IN</th><th>OUT</th><th>비용(USD)</th></tr></thead>
          <tbody>
            {usage.map((u) => (
              <tr key={u.id}>
                <td className="mono xs">{u.createdAt.slice(0, 16).replace('T', ' ')}</td>
              <td className="mono">{u.userId == null ? '비로그인' : `#${u.userId}`}</td>
                <td className="mono xs">{u.modelName}</td>
                <td><span className="chip navy">{u.requestType}</span></td>
                <td className="mono">{u.inputTokens.toLocaleString()}</td>
                <td className="mono">{u.outputTokens.toLocaleString()}</td>
                <td className="mono">${u.cost}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
