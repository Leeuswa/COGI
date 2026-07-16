/*
 * 성장 추이 (GRW-001, API-050, FR-71~72)
 * 주 단위 이슈 발생/해결 빈도. 차트 라이브러리 대신 픽셀 셀을 쌓아 그린다
 * (.pix-chart) — 도트 감성 유지 + 번들 가벼움, 일석이조.
 * 셀 1칸 = 이슈 1건. 산호색=발생, 민트=그중 해결.
 */
import { useEffect, useState } from 'react';
import * as api from '../../api/client';
import { useGame } from '../../context/GameContext';
import { Link } from 'react-router-dom';
import { PageHead } from '../../components/ui';

export default function Growth() {
  const { S } = useGame();
  const [trend, setTrend] = useState(null);
  const [period, setPeriod] = useState('4W');
  const [memberId, setMemberId] = useState('ALL'); // 팀 전체 또는 특정 팀원 (FR-72)
  const [members, setMembers] = useState([]);

  useEffect(() => { api.getTeamMembers(1).then(setMembers); }, []);
  useEffect(() => {
    // 팀원 선택 시 memberId 쿼리로 좁혀서 조회 — 서버가 그 사람 이슈만 집계해서 준다
    api.getGrowthTrend(1, { period, ...(memberId !== 'ALL' && { memberId }) }).then(setTrend);
  }, [period, memberId]);

  const total = (trend || []).reduce((a, w) => a + w.issueCount, 0);
  const solved = (trend || []).reduce((a, w) => a + w.resolved, 0);
  const first = trend?.[0]?.issueCount ?? 0;
  const last = trend?.[trend.length - 1]?.issueCount ?? 0;
  const improving = last < first;

  return (
    <main className="app-main">
      <PageHead
        badge="GROWTH" badgeCls="co"
        title="성장 추이"
        lead={"같은 실수가 줄어드는 게 진짜 성장이에요.\n막대가 낮아지고 해결률이 올라갈수록 코기가 기뻐합니다."}
      />

      <div className="filter-bar">
        {/* 팀원별 필터 (FR-72) — 팀장이 특정 팀원의 추이만 떼어 본다 */}
        <select value={memberId} onChange={(e) => setMemberId(e.target.value)}>
          <option value="ALL">팀 전체</option>
          {members.map((m) => <option key={m.userId} value={m.userId}>@{m.githubUsername}</option>)}
        </select>
        <select value={period} onChange={(e) => setPeriod(e.target.value)}>
          <option value="4W">최근 4주</option>
          <option value="8W">최근 8주</option>
          <option value="12W">최근 12주</option>
        </select>
      </div>

      {!trend ? (
        <div className="panel"><p className="note">집계 중…</p></div>
      ) : (
        <>
          <div className="panel">
            <div className="row" style={{ marginBottom: 6 }}>
              <h3 style={{ marginBottom: 0 }}>주간 이슈 빈도</h3>
              {/* 첫 주 대비 마지막 주 감소율 — 이 페이지의 한 줄 결론 */}
              {trend.length > 1 && trend[0].issueCount > trend[trend.length - 1].issueCount && (
                <span className="trend-badge ml-auto">
                  ▼ {Math.round((1 - trend[trend.length - 1].issueCount / trend[0].issueCount) * 100)}% 감소중
                </span>
              )}
            </div>
            {(() => {
              /* 영역 + 라인 차트 — "발전"이 선의 방향으로 바로 읽히게.
                 아래(옅은 산호 영역) = 미해결 이슈가 줄어드는 모양,
                 위(민트 라인 + 사각 마커) = 해결률이 올라가는 모양. 픽셀 무드는 직각 스텝으로 유지 */
              const W = 720, H = 250, PAD = 48;
              const max = Math.max(...trend.map((w) => w.issueCount), 4);
              const x = (i) => PAD + ((W - PAD * 2) / (trend.length - 1)) * i;
              const yIss = (v) => H - 40 - ((H - 96) * v) / max;           // 이슈 수 (아래 영역)
              const yRate = (r) => 36 + (H - 116) * (1 - r);               // 해결률 0~1 (위 라인)
              // 계단형 경로 (직각 스텝 = 도트 무드)
              const step = (pts) => pts.map((p, i) => (i === 0 ? `M${p[0]},${p[1]}` : `H${p[0]} V${p[1]}`)).join(' ');
              const issPts = trend.map((w, i) => [x(i), yIss(w.issueCount - w.resolved)]);
              const areaPath = `${step(issPts)} H${x(trend.length - 1)} V${yIss(0)} H${issPts[0][0]} Z`;
              const rate = trend.map((w, i) => ({ cx: x(i), cy: yRate(w.resolved / w.issueCount), pct: Math.round((w.resolved / w.issueCount) * 100) }));
              return (
                <svg className="gchart" viewBox={`0 0 ${W} ${H}`} role="img" aria-label="주간 성장 차트">
                  {/* 배경 눈금 */}
                  {[0.25, 0.5, 0.75, 1].map((t) => (
                    <line key={t} x1={PAD} x2={W - PAD} y1={yIss(max * t)} y2={yIss(max * t)} className="grid" />
                  ))}
                  {/* 미해결 이슈 영역 (계단형, 줄어드는 모양) */}
                  <path d={areaPath} className="iss-area" />
                  <path d={step(issPts)} className="iss-line" />
                  {issPts.map(([cx, cy], i) => (
                    <text key={'i' + i} x={cx} y={cy - 8} textAnchor="middle" className="ilab">
                      {trend[i].issueCount - trend[i].resolved}
                    </text>
                  ))}
                  {/* 해결률 상승 라인 — 이 페이지의 주인공 */}
                  <polyline className="rate-line" points={rate.map((p) => `${p.cx},${p.cy}`).join(' ')} />
                  {rate.map((p, i) => (
                    <g key={'r' + i}>
                      <rect x={p.cx - 6} y={p.cy - 6} width="12" height="12" className="rate-dot" />
                      <text x={p.cx} y={p.cy - 13} textAnchor="middle" className="rlab">{p.pct}%</text>
                    </g>
                  ))}
                  {/* x축 라벨 + 바닥선 */}
                  {trend.map((w, i) => (
                    <text key={'x' + i} x={x(i)} y={H - 14} textAnchor="middle" className="xlab2">{w.label}</text>
                  ))}
                  <line x1={PAD} x2={W - PAD} y1={yIss(0)} y2={yIss(0)} className="axis" />
                </svg>
              );
            })()}
            <div className="chart-legend">
              <span><i style={{ background: 'var(--mint)' }} />해결률 (오를수록 성장 ↗)</span>
              <span><i style={{ background: '#ffb3a8' }} />미해결 이슈 (줄어들수록 성장 ↘)</span>
            </div>
          </div>

          <div className="panel-grid c3" style={{ marginTop: 22 }}>
            <div className="panel stat-card">
              <span style={{ fontSize: 20 }}>🐛</span>
              <b className="stat-num">{total}</b>
              <p className="note xs">기간 내 이슈</p>
            </div>
            <div className="panel stat-card">
              <span style={{ fontSize: 20 }}>✅</span>
              <b className="stat-num" style={{ color: 'var(--mint)' }}>{solved}</b>
              <p className="note xs">해결 완료</p>
            </div>
            <div className="panel stat-card">
              <span style={{ fontSize: 20 }}>🔥</span>
              <b className="stat-num" style={{ color: 'var(--coral)' }}>{S.streak}일</b>
              <p className="note xs">연속 학습</p>
            </div>
          </div>

          <div className="panel">
            <h3>{improving ? '📉 좋은 신호예요' : '📈 조금 더 힘내요'}</h3>
            <p style={{ fontSize: 14, lineHeight: 2.1 }}>
              {improving
                ? <>첫 주 {first}건 → 마지막 주 {last}건. 반복 실수가 확실히 줄고 있어요. 이 리듬 그대로!</>
                : <>이슈가 아직 줄지 않았어요. 약점 카드 퀴즈를 하루 1회만 제출해도 흐름이 바뀝니다.</>}
              <br />
              주간 리포트는 <Link to="/app/reports" className="link-line">주간리포트 탭</Link>에서 언제든 확인하고,
              <br />
              원하면 메일로도 보낼 수 있어요.
            </p>
          </div>
        </>
      )}
    </main>
  );
}
