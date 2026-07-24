/*
 * [관리자 탭] AI 사용량 (ADM-001, API-014)
 * 기간 필터 + 비용 합계/상한 경고(FR-81) + 리뷰 응답시간(API-054, FR-77) + 로그 테이블.
 * 데이터 조회는 부모(Admin)가 하고 여긴 그리기만 — 탭 전환 때 재조회를 안 하기 위해서.
 */
import { useState } from 'react';

// 벤더 분류(GEMINI/CLAUDE/OPENAI) + 고정 색상. 모델명 접두사로 판별.
const VENDORS = [
  { key: 'GEMINI', color: '#4c6ef5', match: (s) => s.startsWith('gemini') },
  { key: 'CLAUDE', color: '#f59f00', match: (s) => s.startsWith('claude') },
  { key: 'OPENAI', color: '#12b886', match: (s) => s.startsWith('gpt') || s.startsWith('openai') },
];
const vendorOf = (m) => VENDORS.find((v) => v.match((m || '').toLowerCase()))?.key || null;
const BUDGET_USD = 10; // 벤더별 상한 $10 — 이 금액의 토큰 환산치가 바의 100%

export default function UsageTab({ usage, range, setRange, latency }) {
  // 명세(credit_usage 비고): 관리자 전체 비용상한은 두지 않는다 — 합계는 참고용 표기만
  const totalCost = usage.reduce((a, u) => a + Number(u.cost || 0), 0);

  // 그래프에서 벤더 선을 켜고/끌 수 있게 (기본 전부 표시)
  const [hidden, setHidden] = useState({});
  const toggle = (key) => setHidden((h) => ({ ...h, [key]: !h[key] }));
  const visibleVendors = VENDORS.filter((v) => !hidden[v.key]);

  // 기간 끝(range.to, 기본 오늘) 기준 최근 5일 × 벤더별 토큰 집계 — 기록 없는 날도 0으로 채워 x축을 채운다.
  const SHOW_DAYS = 5, DAY_MS = 86400000;
  const endMs = Date.parse(range.to); // UTC 자정 기준
  const days = [];
  for (let k = SHOW_DAYS - 1; k >= 0; k--) days.push(new Date(endMs - k * DAY_MS).toISOString().slice(0, 10));
  const byDay = {};
  for (const key of days) byDay[key] = {};
  for (const u of usage) {
    const day = (u.createdAt || '').slice(0, 10);
    const bucket = byDay[day]; // 최근 5일에 속하지 않으면 undefined
    if (!bucket) continue;
    const v = vendorOf(u.modelName);
    if (!v) continue;
    bucket[v] = (bucket[v] || 0) + (u.inputTokens || 0) + (u.outputTokens || 0);
  }
  // y스케일: 보이는 벤더들의 일자별 값 중 최댓값
  const maxTokens = Math.max(1, ...days.flatMap((d) => visibleVendors.map((v) => byDay[d][v.key] || 0)));
  // 성장추이(Growth.tsx)와 동일한 좌표계 + Catmull-Rom 부드러운 곡선 → 선이 끊기지 않는다
  const W = 720, H = 210, PAD = 48;
  const n = days.length;
  const xOf = (i) => PAD + ((W - PAD * 2) / Math.max(1, n - 1)) * i;
  const yOf = (v) => H - 34 - ((H - 74) * v) / maxTokens;
  const fmt = (t) => (t >= 1000 ? `${(t / 1000).toFixed(1).replace(/\.0$/, '')}k` : String(t)); // 1840 → 1.8k
  const smooth = (pts) => {
    if (pts.length < 2) return pts.length ? `M${pts[0][0]},${pts[0][1]}` : '';
    let d = `M${pts[0][0]},${pts[0][1]}`;
    for (let i = 0; i < pts.length - 1; i++) {
      const p0 = pts[i - 1] || pts[i], p1 = pts[i], p2 = pts[i + 1], p3 = pts[i + 2] || p2;
      d += ` C${p1[0] + (p2[0] - p0[0]) / 6},${p1[1] + (p2[1] - p0[1]) / 6} ${p2[0] - (p3[0] - p1[0]) / 6},${p2[1] - (p3[1] - p1[1]) / 6} ${p2[0]},${p2[1]}`;
    }
    return d;
  };

  // 벤더별 누적 토큰/비용(기간 전체) — 진행바는 $10 예산 대비 사용률
  const vendorTok = {}, vendorCost = {};
  for (const u of usage) {
    const v = vendorOf(u.modelName);
    if (!v) continue;
    vendorTok[v] = (vendorTok[v] || 0) + (u.inputTokens || 0) + (u.outputTokens || 0);
    vendorCost[v] = (vendorCost[v] || 0) + Number(u.cost || 0);
  }

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
      {/* 일자별 사용량 그래프 — 날짜×모델로 묶어 모델별 선 그래프로 시각화 */}
      <div className="panel">
        <div className="row" style={{ justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 10 }}>
          <b>일자별 토큰 사용량 (벤더별)</b>
          <span className="note sm">최근 5일 · 범례를 눌러 선을 켜고/끌 수 있어요 · 우리 로그(ai_usage_logs) 기준</span>
        </div>
        {usage.length === 0 || days.length === 0 ? (
          <p className="note sm">이 기간에 사용 기록이 없어요.</p>
        ) : (
          <>
            {/* 범례 = 선 토글 버튼 */}
            <div className="row" style={{ flexWrap: 'wrap', gap: 8, marginBottom: 10 }}>
              {VENDORS.map((v) => {
                const on = !hidden[v.key];
                return (
                  <button key={v.key} type="button" onClick={() => toggle(v.key)}
                    title={on ? '숨기기' : '보이기'}
                    style={{ display: 'inline-flex', alignItems: 'center', gap: 6, cursor: 'pointer',
                             fontSize: 12, fontWeight: 700, color: 'var(--navy)', padding: '4px 10px',
                             border: '2px solid var(--navy)', background: on ? '#fff' : 'transparent',
                             opacity: on ? 1 : 0.45, textDecoration: on ? 'none' : 'line-through' }}>
                    <span style={{ width: 10, height: 10, borderRadius: 2, background: v.color, display: 'inline-block' }} />
                    {v.key}
                  </button>
                );
              })}
            </div>
            <svg className="gchart" viewBox={`0 0 ${W} ${H}`} role="img" aria-label="일자별 벤더별 토큰 사용량">
              {/* 그리드 */}
              {[0.25, 0.5, 0.75, 1].map((t) => (
                <line key={t} x1={PAD} x2={W - PAD} y1={yOf(maxTokens * t)} y2={yOf(maxTokens * t)} className="grid" />
              ))}
              {/* 선택된 벤더 곡선 (0 채움으로 끊기지 않음) + 점 + 값 라벨 */}
              {visibleVendors.map((v) => {
                const pts = days.map((d, i) => [xOf(i), yOf(byDay[d][v.key] || 0)]);
                return (
                  <g key={v.key}>
                    <path d={smooth(pts)} fill="none" stroke={v.color} strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
                    {pts.map(([cx, cy], i) => {
                      const t = byDay[days[i]][v.key] || 0;
                      return (
                        <g key={i}>
                          <circle cx={cx} cy={cy} r="3.5" fill="#fff" stroke={v.color} strokeWidth="2.5">
                            <title>{`${days[i]} · ${v.key}\n토큰 ${t.toLocaleString()}`}</title>
                          </circle>
                          {t > 0 && (
                            <text x={cx} y={cy - 8} textAnchor="middle" fontSize="10" fontWeight="700" fill={v.color}>{fmt(t)}</text>
                          )}
                        </g>
                      );
                    })}
                  </g>
                );
              })}
              {/* x축 날짜 라벨 + 축 */}
              {days.map((d, i) => (
                <text key={d} x={xOf(i)} y={H - 10} textAnchor="middle" className="xlab2">{d.slice(5)}</text>
              ))}
              <line x1={PAD} x2={W - PAD} y1={yOf(0)} y2={yOf(0)} className="axis" />
            </svg>
          </>
        )}
      </div>

      {/* 벤더별 누적 사용량 — $10 예산 대비 사용률 (기간 전체) */}
      {usage.length > 0 && (
        <div className="panel">
          <div className="row" style={{ justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 14 }}>
            <b>모델별 누적 사용량</b>
            <span className="note sm">벤더별 ${BUDGET_USD} 예산 대비 사용률</span>
          </div>
          {VENDORS.map((v) => {
            const tok = vendorTok[v.key] || 0;
            const cost = vendorCost[v.key] || 0;
            const rawPct = (cost / BUDGET_USD) * 100;
            const pct = Math.round(rawPct);
            const warn = rawPct >= 90;
            return (
              <div key={v.key} style={{ marginBottom: 14 }}>
                <div className="row" style={{ justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 6 }}>
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 13, fontWeight: 700, color: 'var(--navy)' }}>
                    <span style={{ width: 10, height: 10, borderRadius: 2, background: v.color, display: 'inline-block' }} />
                    {v.key}
                  </span>
                  <span className="note sm">{tok.toLocaleString()} 토큰 · ${cost.toFixed(2)} / ${BUDGET_USD} · {pct}%</span>
                </div>
                <div className={`credit-bar${warn ? ' warn' : ''}`}>
                  <i style={{ width: `${Math.min(100, rawPct)}%`, background: warn ? undefined : v.color }} />
                </div>
              </div>
            );
          })}
        </div>
      )}

      <div className="panel flush">
        <table className="tbl">
          <thead><tr><th>일시</th><th>유저</th><th>모델</th><th>유형</th><th>IN</th><th>OUT</th><th>비용(USD)</th></tr></thead>
          <tbody>
            {usage.map((u) => (
              <tr key={u.id}>
                <td className="mono xs">{u.createdAt.slice(0, 16).replace('T', ' ')}</td>
              <td className="mono">{u.userId == null ? '비로그인' : (u.nickname || `#${u.userId}`)}</td>
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
