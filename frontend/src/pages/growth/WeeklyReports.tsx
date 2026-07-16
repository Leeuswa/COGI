/*
 * 주간 리포트 (RET-002 변경분)
 * [설계 변경] 원안은 "매주 월요일 10시 자동 메일"(FR-75)이었지만,
 * 네비의 주간리포트 탭에서 기간별 리스트를 보고 → 팝업으로 상세를 읽고 →
 * 팝업 맨 아래 오른쪽 [메일로 보내기]를 눌러야 발송되는 방식으로 바꿨다.
 * 백엔드: 월요일 배치는 리포트 '생성'까지만, 발송은 사용자 요청 API로 분리.
 */
import { useEffect, useState } from 'react';
import * as api from '../../api/client';
import { useGame } from '../../context/GameContext';
import { PageHead } from '../../components/ui';
import { catKo } from '../../data/constants';

const fmt = (d) => d.slice(5).replace('-', '/'); // '2026-07-06' → '07/06'

export default function WeeklyReports() {
  const { notify } = useGame();
  const [reports, setReports] = useState([]);
  const [open, setOpen] = useState(null);   // 팝업에 띄운 리포트
  const [busy, setBusy] = useState(false);

  useEffect(() => { api.getWeeklyReports().then(setReports); }, []);

  const sendMail = async () => {
    setBusy(true);
    try {
      await api.sendWeeklyReportMail(open.id);
      notify(`${fmt(open.periodStart)}~${fmt(open.periodEnd)} 리포트를 메일로 보냈어요 📮`);
      setOpen(null);
    } finally { setBusy(false); }
  };

  return (
    <main className="app-main">
      <PageHead badge="WEEKLY" title="주간 리포트"
        lead={"매주 월요일 오전, 지난 한 주의 리포트가 만들어져요.\n열어보고 필요하면 메일로도 보관하세요."} />

      {reports.length === 0 ? (
        <div className="empty"><p>아직 리포트가 없어요. 첫 주가 지나면 여기에 쌓입니다.</p></div>
      ) : (
        reports.map((r) => (
          <button key={r.id} className="panel report-row" onClick={() => setOpen(r)}>
            <b className="mono">{fmt(r.periodStart)} ~ {fmt(r.periodEnd)}</b>
            <span className="chip navy">{catKo(r.topCategory)}</span>
            <span className="note">{r.summary}</span>
            <span className="ml-auto note sm">이슈 {r.issueCount} · 해결 {r.resolvedCount} →</span>
          </button>
        ))
      )}

      {/* 상세 팝업 — 내부 스크롤, 맨 아래 오른쪽에 메일 발송 */}
      {open && (
        <div className="modal-mask" onClick={() => setOpen(null)}>
          <div className="modal" role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
            <h3>📊 {fmt(open.periodStart)} ~ {fmt(open.periodEnd)} 주간 리포트</h3>

            <p className="note" style={{ lineHeight: 2, marginBottom: 16 }}>{open.summary}</p>

            {/* 핵심 수치 3종 + 전주 대비 */}
            <div className="panel-grid c3" style={{ marginBottom: 20 }}>
              <div className="panel tc">
                <p className="note xs">발생 이슈</p>
                <b className="stat-num">{open.issueCount}</b>
                {open.prevIssueCount != null && (
                  <p className={`note xs ${open.issueCount <= open.prevIssueCount ? 'ok' : 'err'}`}>
                    전주 {open.prevIssueCount} → {open.issueCount <= open.prevIssueCount ? '▼' : '▲'}
                    {Math.abs(Math.round((1 - open.issueCount / open.prevIssueCount) * 100))}%
                  </p>
                )}
              </div>
              <div className="panel tc">
                <p className="note xs">해결</p>
                <b className="stat-num" style={{ color: 'var(--mint)' }}>{open.resolvedCount}</b>
                <p className="note xs">해결률 {Math.round((open.resolvedCount / open.issueCount) * 100)}%</p>
              </div>
              <div className="panel tc">
                <p className="note xs">최다 카테고리</p>
                <b style={{ fontSize: 13, fontFamily: 'Silkscreen, monospace' }}>{catKo(open.topCategory)}</b>
              </div>
            </div>

            {/* 카테고리별 분포 — 픽셀 미니 바 */}
            <h3>카테고리별 발생</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 9, marginBottom: 20 }}>
              {open.categories.map((cat) => (
                <div key={cat.name} className="row" style={{ gap: 10 }}>
                  <span className="mono xs" style={{ width: 130 }}>{catKo(cat.name)}</span>
                  <span className="hbar"><i style={{ width: (cat.count / open.issueCount) * 100 + '%' }} /></span>
                  <b className="mono" style={{ fontSize: 12 }}>{cat.count}</b>
                </div>
              ))}
            </div>

            {/* 학습 활동 */}
            <h3>이번 주 학습 활동</h3>
            <p style={{ fontSize: 13.5, lineHeight: 2, marginBottom: 20 }}>
              퀴즈 제출 <b>{open.quizSubmits}회</b> · 정답률 <b>{open.correctRate}%</b> ·
              주말 기준 연속 학습 <b style={{ color: 'var(--coral)' }}>{open.streakEnd}일</b>
            </p>

            {/* 다음 주 추천 */}
            <h3>코기의 다음 주 추천</h3>
            <ol className="keypoints" style={{ marginBottom: 4 }}>
              {open.actions.map((a, i) => <li key={i}>{a}</li>)}
            </ol>

            {/* 하단 액션 바 — 오른쪽 끝이 메일 발송 */}
            <div className="row" style={{ marginTop: 24 }}>
              <button className="btn wh sm" onClick={() => setOpen(null)}>닫기</button>
              <button className="btn co sm ml-auto" onClick={sendMail} disabled={busy}>
                {busy ? '발송 중…' : '📮 메일로 보내기'}
              </button>
            </div>
          </div>
        </div>
      )}
    </main>
  );
}
