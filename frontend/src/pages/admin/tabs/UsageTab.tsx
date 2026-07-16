/*
 * [관리자 탭] AI 사용량 (ADM-001, API-014)
 * 기간 필터 + 비용 합계/상한 경고(FR-81) + 리뷰 응답시간(API-054, FR-77) + 로그 테이블.
 * 데이터 조회는 부모(Admin)가 하고 여긴 그리기만 — 탭 전환 때 재조회를 안 하기 위해서.
 */
export default function UsageTab({ usage, range, setRange, latency }) {
  // 명세(credit_usage 비고): 관리자 전체 비용상한은 두지 않는다 — 합계는 참고용 표기만
  const totalCost = usage.reduce((a, u) => a + u.cost, 0);

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
      <div className="panel flush">
        <table className="tbl">
          <thead><tr><th>일시</th><th>유저</th><th>모델</th><th>유형</th><th>IN</th><th>OUT</th><th>비용(USD)</th></tr></thead>
          <tbody>
            {usage.map((u) => (
              <tr key={u.id}>
                <td className="mono xs">{u.createdAt.slice(0, 16).replace('T', ' ')}</td>
                <td className="mono">#{u.userId}</td>
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
