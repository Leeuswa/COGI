/*
 * 주간 리포트 (RET-002 / FR-75)
 * 매주 월요일 오전 배치가 지난 한 주의 리포트를 '생성 + 메일 자동 발송'까지 처리한다.
 * 이 화면은 쌓인 리포트를 기간별로 보고 → 팝업으로 상세를 읽는 '조회 전용'.
 * (수동 재발송 API는 뒀지만 UI에선 안 씀 — 나중에 재발송 기능 붙일 때 재사용)
 */
import { useEffect, useState } from 'react';
import * as api from '../../api/client';
import { PageHead } from '../../components/ui';
import { catKo } from '../../data/constants';

const fmt = (d) => d.slice(5).replace('-', '/'); // '2026-07-06' → '07/06'

// 3칸 stat 카드 공통 스타일 — margin-top 0(형제 margin 상쇄) + 높이 통일 + 세로 중앙
const sc = { marginTop: 0, minHeight: 120, display: 'flex', flexDirection: 'column' as const, justifyContent: 'center', gap: 6 };

export default function WeeklyReports() {
  const [reports, setReports] = useState([]);
  const [open, setOpen] = useState(null);   // 팝업에 띄운 리포트

  useEffect(() => { api.getWeeklyReports().then(setReports); }, []);

  return (
    <main className="app-main">
      <PageHead badge="WEEKLY" title="주간 리포트"
        lead={"매주 월요일 오전, 지난 한 주의 리포트가 만들어져 메일로도 자동 발송돼요.\n여기서 지난 리포트를 다시 볼 수 있어요."} />

      {reports.length === 0 ? (
        <div className="empty"><p>아직 리포트가 없어요. 첫 주가 지나면 여기에 쌓입니다.</p></div>
      ) : (
        reports.map((r) => (
          <button key={r.id} className="panel report-row" onClick={() => setOpen(r)}>
            <b className="mono">{fmt(r.periodStart)} ~ {fmt(r.periodEnd)}</b>
            <span className="chip navy">{catKo(r.topCategory)}</span>
            <span className="note">{r.summary}</span>
            <span className="ml-auto note sm" style={{ fontWeight: 700 }}>이슈 {r.issueCount} · 해결 {r.resolvedCount} →</span>
          </button>
        ))
      )}

      {/* 상세 팝업 — 내부 스크롤, 맨 아래 오른쪽에 메일 발송 */}
      {open && (
        <div className="modal-mask" onClick={() => setOpen(null)}>
          <div className="modal" role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
            <h3>{fmt(open.periodStart)} ~ {fmt(open.periodEnd)} 주간 리포트</h3>

            <p className="note" style={{ lineHeight: 2, marginBottom: 16 }}>{open.summary}</p>

            {/* 핵심 수치 3종 + 전주 대비 */}
            {/* 3칸 균등: margin 0으로 세로 어긋남 제거 + minHeight로 높이 통일, 내용 세로 중앙 정렬 */}
            <div className="panel-grid c3" style={{ marginBottom: 20, gridTemplateColumns: 'repeat(3, minmax(0,1fr))' }}>
              <div className="panel tc" style={sc}>
                <p className="note xs">발생 이슈</p>
                <b className="stat-num">{open.issueCount}<span className="unit">건</span></b>
                {open.prevIssueCount != null && (
                  <p className={`note xs ${open.issueCount <= open.prevIssueCount ? 'ok' : 'err'}`}>
                    전주 {open.prevIssueCount} → {Math.abs(Math.round((1 - open.issueCount / open.prevIssueCount) * 100))}%
                    {open.issueCount <= open.prevIssueCount ? ' 감소' : ' 증가'}
                  </p>
                )}
              </div>
              <div className="panel tc" style={sc}>
                <p className="note xs">해결</p>
                <b className="stat-num" style={{ color: 'var(--mint)' }}>{open.resolvedCount}<span className="unit">건</span></b>
                <p className="note xs">해결률 {Math.round((open.resolvedCount / open.issueCount) * 100)}%</p>
              </div>
              <div className="panel tc" style={sc}>
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
            {/* 발송은 매주 월 자동 → 수동 버튼 없이 확인용 팝업 */}
            <div className="row" style={{ marginTop: 24 }}>
              <span className="note sm">매주 월요일 아침 9시에 이 리포트가 이메일로 자동 발송돼요.</span>
              <button className="btn co sm ml-auto" onClick={() => setOpen(null)}>닫기</button>
            </div>
          </div>
        </div>
      )}
    </main>
  );
}
