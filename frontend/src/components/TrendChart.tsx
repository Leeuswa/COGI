/*
 * 주간 이슈 추이 곡선 차트.
 * 성장 추이 화면(Growth.tsx)의 그라데이션 영역 차트와 같은 모양이고,
 * 스타일(.gchart)도 그대로 쓴다. 대시보드 요약에서도 같은 그림을 보여주려고 뽑아 뒀다.
 *
 * data: [{ label, issueCount, resolved }]
 */
type Week = { label: string; issueCount: number; resolved: number };

// Catmull-Rom을 베지어로 바꿔 점 사이를 부드럽게 잇는다
const smooth = (pts: number[][]) => {
  if (pts.length < 2) return pts.map((p) => `M${p[0]},${p[1]}`).join(' ');
  let d = `M${pts[0][0]},${pts[0][1]}`;
  for (let i = 0; i < pts.length - 1; i++) {
    const p0 = pts[i - 1] || pts[i], p1 = pts[i], p2 = pts[i + 1], p3 = pts[i + 2] || p2;
    d += ` C${p1[0] + (p2[0] - p0[0]) / 6},${p1[1] + (p2[1] - p0[1]) / 6}`
      + ` ${p2[0] - (p3[0] - p1[0]) / 6},${p2[1] - (p3[1] - p1[1]) / 6} ${p2[0]},${p2[1]}`;
  }
  return d;
};

export default function TrendChart({ data, height = 200 }: { data: Week[]; height?: number }) {
  if (!data?.length) return null;

  const W = 720, H = height, PAD = 44;
  // 눈금 최댓값을 4로 못 박으면 실제 최대가 2일 때 위 절반이 빈다. 데이터에 맞추되 최소 2는 유지
  const max = Math.max(...data.map((w) => w.issueCount), 2);
  const n = data.length;
  // 한 주만 있으면 나누기가 0이 되니 가운데에 찍는다
  const x = (i: number) => (n === 1 ? W / 2 : PAD + ((W - PAD * 2) / (n - 1)) * i);
  const y = (v: number) => H - 34 - ((H - 62) * v) / max; // 위아래 여백을 줄여 선이 액자를 채우게

  const issPts = data.map((w, i) => [x(i), y(w.issueCount)]);
  const resPts = data.map((w, i) => [x(i), y(w.resolved ?? 0)]);
  const issLine = smooth(issPts);
  const issArea = `${issLine} L${issPts[n - 1][0]},${y(0)} L${issPts[0][0]},${y(0)} Z`;

  return (
    <svg className="gchart" viewBox={`0 0 ${W} ${H}`} role="img" aria-label="주간 발생·해결 추이">
      <defs>
        <linearGradient id="trendGrad" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#ff6b57" stopOpacity="0.45" />
          <stop offset="100%" stopColor="#ff6b57" stopOpacity="0" />
        </linearGradient>
      </defs>

      {/* 눈금선 */}
      {[0.25, 0.5, 0.75, 1].map((t) => (
        <line key={t} x1={PAD} x2={W - PAD} y1={y(max * t)} y2={y(max * t)} className="grid" />
      ))}

      <path d={issArea} fill="url(#trendGrad)" />
      <path d={issLine} className="area-line-open" />
      {/* 해결 곡선. 값이 0이면 축선과 겹쳐 안 보이므로 살짝 띄워 그린다 */}
      <path d={smooth(resPts)} className="area-line-resolved" transform="translate(0,-3)" />

      {/* 주별 마커와 발생 건수 */}
      {data.map((w, i) => (
        <g key={'m' + i}>
          <circle cx={x(i)} cy={y(w.resolved ?? 0)} r="5" className="dot-resolved" />
          <circle cx={x(i)} cy={y(w.issueCount)} r="6" className="dot-open" />
          <text x={x(i)} y={y(w.issueCount) - 12} textAnchor="middle" className="ilab">{w.issueCount}</text>
        </g>
      ))}
      {data.map((w, i) => (
        <text key={'x' + i} x={x(i)} y={H - 10} textAnchor="middle" className="xlab2">{w.label}</text>
      ))}
      <line x1={PAD} x2={W - PAD} y1={y(0)} y2={y(0)} className="axis" />
    </svg>
  );
}
