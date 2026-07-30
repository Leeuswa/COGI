/*
 * [관리자 탭] AI 사용량 (ADM-001, API-014)
 * 기간 필터 + 비용 합계/상한 경고(FR-81) + 리뷰 응답시간(API-054, FR-77) + 로그 테이블.
 * 데이터 조회는 부모(Admin)가 하고 여긴 그리기만 — 탭 전환 때 재조회를 안 하기 위해서.
 */
import { useState } from 'react';

// 벤더 분류 + 고정 색상. 모델명 접두사로 판별.
// real=true 는 벤더 Admin API로 실사용량을 받아오는 벤더 — 그래프/누적바도 그 값을 쓴다.
// GEMINI/GROQ는 usage API가 없어 우리 자체집계(ai_usage_logs)를 그대로 쓴다.
const VENDORS = [
  { key: 'GEMINI', color: '#4c6ef5', real: false, match: (s) => s.startsWith('gemini') },
  { key: 'GROQ',   color: '#e8590c', real: false, match: (s) => s.startsWith('llama') || s.startsWith('groq') },
  { key: 'CLAUDE', color: '#f59f00', real: true,  match: (s) => s.startsWith('claude') },
  { key: 'OPENAI', color: '#12b886', real: true,  match: (s) => s.startsWith('gpt') || s.startsWith('openai') },
];
const REAL_KEYS = new Set(VENDORS.filter((v) => v.real).map((v) => v.key));
const vendorOf = (m) => VENDORS.find((v) => v.match((m || '').toLowerCase()))?.key || null;
const BUDGET_USD = 10; // 벤더별 상한 $10 — 이 금액의 토큰 환산치가 바의 100%

export default function UsageTab({ usage, range, setRange, latency, providerUsage = [] }) {
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
  // 하이브리드: 벤더 Admin API는 '오늘' 사용량을 최대 하루 늦게 준다. 그래서 조회 끝이 실제 오늘일 때만,
  // 오늘 하루는 벤더값 대신 우리 자체집계(ai_usage_logs)로 실시간 표시하고 어제 이하는 벤더 실측을 쓴다.
  // (오늘치 비용은 우리 계산 = 추정, 다음 날 벤더 버킷이 나오면 실측으로 대체됨)
  const _t = new Date();
  const realToday = `${_t.getFullYear()}-${String(_t.getMonth() + 1).padStart(2, '0')}-${String(_t.getDate()).padStart(2, '0')}`;
  const liveDay = range.to === realToday ? realToday : null;  // null이면 과거 조회 → 전부 벤더 실측
  // 자체집계: GEMINI/GROQ는 전 기간, CLAUDE/OPENAI(real)는 '오늘'만(실시간).
  for (const u of usage) {
    const day = (u.createdAt || '').slice(0, 10);
    const bucket = byDay[day]; // 최근 5일에 속하지 않으면 undefined
    if (!bucket) continue;
    const v = vendorOf(u.modelName);
    if (!v) continue;
    if (REAL_KEYS.has(v) && day !== liveDay) continue; // real 벤더의 과거일은 아래 벤더 실측이 채움
    bucket[v] = (bucket[v] || 0) + (u.inputTokens || 0) + (u.outputTokens || 0);
  }
  // CLAUDE/OPENAI — 벤더 Admin API 실사용량. '오늘'(liveDay)은 위 자체집계가 채우므로 건너뛴다.
  for (const p of providerUsage) {
    const bucket = byDay[p.date];
    if (!bucket || !REAL_KEYS.has(p.vendor) || p.date === liveDay) continue;
    bucket[p.vendor] = (bucket[p.vendor] || 0) + (p.inputTokens || 0) + (p.outputTokens || 0);
  }
  // y스케일: 보이는 벤더들의 일자별 값 중 최댓값
  const maxTokens = Math.max(1, ...days.flatMap((d) => visibleVendors.map((v) => byDay[d][v.key] || 0)));
  // 날짜별 밴드 좌표계 — 밴드 안에 보이는 벤더 수만큼 막대를 나란히 그린다.
  const W = 720, H = 210, PAD = 48;
  const n = days.length;
  const band = (W - PAD * 2) / Math.max(1, n);   // 하루가 차지하는 폭
  const cxOf = (i) => PAD + band * (i + 0.5);     // 날짜 밴드 중심
  const yOf = (v) => H - 34 - ((H - 74) * v) / maxTokens;
  const fmt = (t) => (t >= 1000 ? `${(t / 1000).toFixed(1).replace(/\.0$/, '')}k` : String(t)); // 1840 → 1.8k

  // 벤더별 누적 토큰/비용(기간 전체) — 진행바는 $10 예산 대비 사용률
  // 그래프와 같은 소스·같은 하이브리드 규칙 — GEMINI/GROQ는 자체집계, CLAUDE/OPENAI는 오늘=자체집계·과거=벤더.
  const vendorTok = {}, vendorCost = {};
  for (const u of usage) {
    const v = vendorOf(u.modelName);
    if (!v) continue;
    if (REAL_KEYS.has(v) && (u.createdAt || '').slice(0, 10) !== liveDay) continue;
    vendorTok[v] = (vendorTok[v] || 0) + (u.inputTokens || 0) + (u.outputTokens || 0);
    vendorCost[v] = (vendorCost[v] || 0) + Number(u.cost || 0);
  }
  for (const p of providerUsage) {
    if (!REAL_KEYS.has(p.vendor) || p.date === liveDay) continue;
    vendorTok[p.vendor] = (vendorTok[p.vendor] || 0) + (p.inputTokens || 0) + (p.outputTokens || 0);
    vendorCost[p.vendor] = (vendorCost[p.vendor] || 0) + Number(p.cost || 0);
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
          <span className="note sm">최근 5일 · 범례를 눌러 켜고/끌 수 있어요 · CLAUDE·OPENAI는 어제까지 벤더 실측, 오늘은 실시간 추정(우리 로그) · GEMINI·GROQ는 우리 로그(ai_usage_logs)</span>
        </div>
        {/* CLAUDE/OPENAI는 벤더 Admin API(providerUsage)로 따로 들어오므로, 자체집계가 비어도 그린다 */}
        {(usage.length === 0 && providerUsage.length === 0) || days.length === 0 ? (
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
              {/* 날짜별 밴드 안에 보이는 벤더 막대를 나란히 */}
              {days.map((d, i) => {
                const groupW = band * 0.7;                       // 밴드의 70%만 막대에 사용
                const bw = groupW / Math.max(1, visibleVendors.length);
                const startX = cxOf(i) - groupW / 2;
                const base = yOf(0);
                return visibleVendors.map((v, j) => {
                  const t = byDay[d][v.key] || 0;
                  const x = startX + bw * j;
                  const y = yOf(t);
                  const w = Math.max(1, bw - 2);
                  return (
                    <g key={v.key}>
                      <rect x={x} y={y} width={w} height={Math.max(0, base - y)} rx="2" fill={v.color}>
                        <title>{`${d} · ${v.key}\n토큰 ${t.toLocaleString()}`}</title>
                      </rect>
                      {t > 0 && (
                        <text x={x + w / 2} y={y - 4} textAnchor="middle" fontSize="9" fontWeight="700" fill={v.color}>{fmt(t)}</text>
                      )}
                    </g>
                  );
                });
              })}
              {/* x축 날짜 라벨 + 축 */}
              {days.map((d, i) => (
                <text key={d} x={cxOf(i)} y={H - 10} textAnchor="middle" className="xlab2">{d.slice(5)}</text>
              ))}
              <line x1={PAD} x2={W - PAD} y1={yOf(0)} y2={yOf(0)} className="axis" />
            </svg>
          </>
        )}
      </div>

      {/* 벤더별 누적 사용량 — $10 예산 대비 사용률 (기간 전체) */}
      {(usage.length > 0 || providerUsage.length > 0) && (
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
