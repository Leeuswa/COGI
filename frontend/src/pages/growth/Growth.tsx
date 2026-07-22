/*
 * 성장 추이 (GRW-001, API-050, FR-71~72)
 * 주 단위 이슈 발생/해결 빈도. 차트 라이브러리 대신 SVG로 직접 그린다(도트 무드 + 번들 경량).
 * - 개별/팀총합 → 단일선(발생 영역 + 해결률 라인)
 * - 팀장이 "팀 전체" → 팀원별 발생 수 멀티라인 겹쳐보기(compare)
 */
import { useEffect, useState } from 'react';
import * as api from '../../api/client';
import { useGame } from '../../context/GameContext';
import { useAuth } from '../../context/AuthContext';
import { Link } from 'react-router-dom';
import { PageHead } from '../../components/ui';

const COMPARE_COLORS = ['#ff6b57', '#4ec9a4', '#1b2a4a', '#c9862e', '#8b96b5', '#e06fae'];

export default function Growth() {
  const { S } = useGame();
  const { user } = useAuth();
  const [trend, setTrend] = useState(null);
  const [compare, setCompare] = useState(null); // { labels, series }
  const [period, setPeriod] = useState('4W');
  const [memberId, setMemberId] = useState('ALL'); // 팀 전체 또는 특정 팀원 (FR-72)
  const [members, setMembers] = useState([]);
  const [repos, setRepos] = useState([]);
  const [repoId, setRepoId] = useState(null); // 선택한 레포(팀)

  // 내가 속한 레포 목록 → 첫 레포를 기본 선택
  useEffect(() => {
    api.getMyLinkedRepos().then((list) => {
      setRepos(list);
      if (list.length > 0) setRepoId(list[0].repoId);
    });
  }, []);

  // 선택한 레포의 팀원 목록. 레포 바뀌면 팀원 필터는 '팀 전체'로 초기화
  useEffect(() => {
    if (repoId == null) return;
    setMemberId('ALL');
    api.getTeamMembers(repoId).then(setMembers);
  }, [repoId]);

  // 팀원 목록에서 "나"인지 판별 (userId 우선, 없으면 githubUsername)
  const isMe = (m) =>
    (user?.userId != null && String(m.userId) === String(user.userId)) ||
    (!!user?.githubUsername && m.githubUsername === user.githubUsername);
  const myRole = members.find(isMe)?.role;
  // 팀장은 전원 개별 조회 가능, 팀원은 본인만 (남은 드롭다운에 안 보임 = 서버 403과 별개로 UX에서도 차단)
  const selectableMembers = myRole === 'OWNER' ? members : members.filter(isMe);
  const compareMode = memberId === 'ALL' && myRole === 'OWNER';

  useEffect(() => {
    if (repoId == null) return;
    // 항상 발생/해결 추이(trend)를 받고, 팀장 팀전체면 겹쳐보기(compare)도 추가로 받는다
    api.getGrowthTrend(repoId, { period, ...(memberId !== 'ALL' && { memberId }) }).then(setTrend);
    if (compareMode) api.getGrowthCompare(repoId, { period }).then(setCompare);
    else setCompare(null);
  }, [repoId, period, memberId, compareMode]);

  // 단일선 요약 지표 (compare 모드에선 안 쓰임)
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
        lead={"반복하던 실수가 줄어드는 게 진짜 성장이에요.\n발생 그래프가 내려가고 해결이 늘수록 코기도 기뻐해요."}
      />

      <div className="filter-bar">
        {/* 레포(팀) 선택 — 1개여도 항상 노출(어느 레포 기준인지 보이게) */}
        {repos.length > 0 && (
          <select value={repoId ?? ''} onChange={(e) => setRepoId(Number(e.target.value))}>
            {repos.map((r) => <option key={r.repoId} value={r.repoId}>{r.repoName}</option>)}
          </select>
        )}
        {/* 팀원별 필터 (FR-72) — 팀장이 특정 팀원의 추이만 떼어 본다 */}
        <select value={memberId} onChange={(e) => setMemberId(e.target.value)}>
          <option value="ALL">팀 전체</option>
          {selectableMembers.map((m) => (
            <option key={m.userId} value={m.userId}>@{m.githubUsername}{isMe(m) ? ' (나)' : ''}</option>
          ))}
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
              /* 모던 그라데이션 영역 차트: 발생 곡선(그라데이션) + 해결 곡선. 두 곡선 간격=미해결. */
              const W = 720, H = 250, PAD = 48;
              const max = Math.max(...trend.map((w) => w.issueCount), 4);
              const n = trend.length;
              const x = (i) => PAD + ((W - PAD * 2) / (n - 1)) * i;
              const y = (v) => H - 44 - ((H - 96) * v) / max;
              // Catmull-Rom → 베지어 곡선(부드럽게)
              const smooth = (pts) => {
                if (pts.length < 2) return pts.map((p) => `${p[0]},${p[1]}`).join(' ');
                let d = `M${pts[0][0]},${pts[0][1]}`;
                for (let i = 0; i < pts.length - 1; i++) {
                  const p0 = pts[i - 1] || pts[i], p1 = pts[i], p2 = pts[i + 1], p3 = pts[i + 2] || p2;
                  d += ` C${p1[0] + (p2[0] - p0[0]) / 6},${p1[1] + (p2[1] - p0[1]) / 6} ${p2[0] - (p3[0] - p1[0]) / 6},${p2[1] - (p3[1] - p1[1]) / 6} ${p2[0]},${p2[1]}`;
                }
                return d;
              };
              const issPts = trend.map((w, i) => [x(i), y(w.issueCount)]);
              const resPts = trend.map((w, i) => [x(i), y(w.resolved)]);
              const issLine = smooth(issPts);
              const issArea = `${issLine} L${issPts[n - 1][0]},${y(0)} L${issPts[0][0]},${y(0)} Z`;
              return (
                <svg className="gchart" viewBox={`0 0 ${W} ${H}`} role="img" aria-label="주간 발생/해결 추이">
                  <defs>
                    <linearGradient id="issGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#ff6b57" stopOpacity="0.45" />
                      <stop offset="100%" stopColor="#ff6b57" stopOpacity="0" />
                    </linearGradient>
                  </defs>
                  {[0.25, 0.5, 0.75, 1].map((t) => (
                    <line key={t} x1={PAD} x2={W - PAD} y1={y(max * t)} y2={y(max * t)} className="grid" />
                  ))}
                  {/* 발생 영역 + 곡선 */}
                  <path d={issArea} fill="url(#issGrad)" />
                  <path d={issLine} className="area-line-open" />
                  {/* 해결 곡선 */}
                  <path d={smooth(resPts)} className="area-line-resolved" />
                  {/* 마커 + 발생 수 라벨 */}
                  {trend.map((w, i) => (
                    <g key={'m' + i}>
                      <circle cx={x(i)} cy={y(w.resolved)} r="5" className="dot-resolved" />
                      <circle cx={x(i)} cy={y(w.issueCount)} r="6" className="dot-open" />
                      <text x={x(i)} y={y(w.issueCount) - 12} textAnchor="middle" className="ilab">{w.issueCount}</text>
                    </g>
                  ))}
                  {trend.map((w, i) => (
                    <text key={'x' + i} x={x(i)} y={H - 12} textAnchor="middle" className="xlab2">{w.label}</text>
                  ))}
                  <line x1={PAD} x2={W - PAD} y1={y(0)} y2={y(0)} className="axis" />
                </svg>
              );
            })()}
            <div className="chart-legend">
              <span><i style={{ background: 'var(--coral)' }} />발생 (줄어들수록 성장 ↘)</span>
              <span><i style={{ background: 'var(--mint)' }} />해결 (많을수록 성장 ↗)</span>
            </div>
          </div>

          {/* 팀장이 "팀 전체" 볼 때만: 팀원별 발생 수 겹쳐보기 (총합 차트 아래) */}
          {compareMode && compare && (
            <div className="panel" style={{ marginTop: 18 }}>
              <div className="row" style={{ marginBottom: 6 }}>
                <h3 style={{ marginBottom: 0 }}>팀원별 주간 이슈 발생 (겹쳐보기)</h3>
                <span className="note sm ml-auto">선이 내려갈수록 그 팀원의 실수가 줄어드는 거예요</span>
              </div>
              {(() => {
                const W = 720, H = 250, PAD = 48;
                const labels = compare.labels;
                const series = compare.series;
                const n = Math.max(1, labels.length);
                const max = Math.max(1, ...series.flatMap((s) => s.issues));
                const x = (i) => PAD + ((W - PAD * 2) / Math.max(1, n - 1)) * i;
                const y = (v) => H - 40 - ((H - 96) * v) / max;
                return (
                  <svg className="gchart" viewBox={`0 0 ${W} ${H}`} role="img" aria-label="팀원별 주간 이슈 겹쳐보기">
                    {[0.25, 0.5, 0.75, 1].map((t) => (
                      <line key={t} x1={PAD} x2={W - PAD} y1={y(max * t)} y2={y(max * t)} className="grid" />
                    ))}
                    {series.map((s, si) => {
                      const color = COMPARE_COLORS[si % COMPARE_COLORS.length];
                      const pts = s.issues.map((v, i) => `${x(i)},${y(v)}`).join(' ');
                      return (
                        <g key={s.member}>
                          <polyline points={pts} fill="none" stroke={color} strokeWidth="3" />
                          {s.issues.map((v, i) => (
                            <g key={i}>
                              <rect x={x(i) - 4} y={y(v) - 4} width="8" height="8" fill={color} />
                              <text x={x(i)} y={y(v) - 11} textAnchor="middle" className="xlab2" style={{ fill: color }}>{v}</text>
                            </g>
                          ))}
                        </g>
                      );
                    })}
                    {labels.map((lb, i) => (
                      <text key={i} x={x(i)} y={H - 14} textAnchor="middle" className="xlab2">{lb}</text>
                    ))}
                    <line x1={PAD} x2={W - PAD} y1={y(0)} y2={y(0)} className="axis" />
                  </svg>
                );
              })()}
              <div className="chart-legend">
                {compare.series.map((s, si) => (
                  <span key={s.member}>
                    <i style={{ background: COMPARE_COLORS[si % COMPARE_COLORS.length] }} />{s.member}
                  </span>
                ))}
              </div>
            </div>
          )}

          <div className="panel-grid c3 stat-grid" style={{ marginTop: 22 }}>
            <div className="panel stat-card">
              <span className="stat-num">{total}<span className="unit">건</span></span>
              <p className="note xs">기간 내 이슈</p>
            </div>
            <div className="panel stat-card">
              <span className="stat-num" style={{ color: 'var(--mint)' }}>{solved}<span className="unit">건</span></span>
              <p className="note xs">해결 완료</p>
            </div>
            <div className="panel stat-card">
              <span className="stat-num" style={{ color: 'var(--coral)' }}>{S.streak}<span className="unit">일</span></span>
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
