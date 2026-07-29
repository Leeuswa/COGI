/*
 * 주간 리포트 (RET-002 / FR-75)
 * 매주 월요일 오전 배치가 지난 한 주의 리포트를 '생성 + 메일 자동 발송'까지 처리한다.
 * 이 화면은 쌓인 리포트를 기간별로 보고 → 팝업으로 상세를 읽는 '조회 전용'.
 * (수동 재발송 API는 뒀지만 UI에선 안 씀 — 나중에 재발송 기능 붙일 때 재사용)
 */
import { useEffect, useState } from 'react';
import * as api from '../../api/client';
import { PageHead } from '../../components/ui';
import { catKo, sevKo } from '../../data/constants';
import useIsMobile from '../../hooks/useIsMobile';
import MobileReports from '../mobile/MobileReports';

const fmt = (d) => d.slice(5).replace('-', '/'); // '2026-07-06' → '07/06'

// 심각도 → 칩 색 (기존 .chip 클래스 재사용)
const SEV_CHIP = { CRITICAL: 'hi', MAJOR: 'mid', MINOR: 'low' };
// 설명 한 줄로 — 코드블록/백틱 걷어내고 공백 정리 후 90자
const brief = (d) => {
  const t = (d || '').replace(/```[\s\S]*?```/g, '').replace(/`([^`]+)`/g, '$1').replace(/\s+/g, ' ').trim();
  return t.length > 90 ? t.slice(0, 90) + '…' : t;
};

// 3칸 stat 카드 공통 스타일 — margin-top 0(형제 margin 상쇄) + 높이 통일 + 세로 중앙
const sc = { marginTop: 0, minHeight: 120, display: 'flex', flexDirection: 'column' as const, justifyContent: 'center', gap: 6 };

// 폰이면 데스크톱 본체를 아예 mount하지 않는다.
// 한 컴포넌트 안에서 갈랐더니 조건부 return 위의 useEffect가 그대로 돌아 같은 API를 두 번 때렸다.
// getWeaknessStats처럼 조회할 때마다 통계를 지우고 다시 넣는 API는 두 번 겹치면 한쪽이 빈 목록을 받는다.
export default function WeeklyReports() {
  return useIsMobile() ? <MobileReports /> : <DesktopWeeklyReports />;
}

function DesktopWeeklyReports() {
  const [reports, setReports] = useState([]);
  const [open, setOpen] = useState(null);   // 팝업에 띄운 리포트
  const [drill, setDrill] = useState(null);  // { status, issues } — 건수 클릭 시 뜨는 이슈 목록
  const [drillRepo, setDrillRepo] = useState('ALL'); // 이슈 목록 레포 필터

  useEffect(() => { api.getWeeklyReports().then(setReports); }, []);

  // 발생/해결 건수 클릭 → 그 주 이슈를 하나하나 띄운다 (해결 탭은 해결된 것만)
  const openDrill = async (status) => {
    const issues = await api.getWeeklyReportIssues(open.id, status);
    setDrillRepo('ALL');
    setDrill({ status, issues });
  };


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
              <button className="panel tc drill-card" style={sc} onClick={() => openDrill('OPEN')}>
                <p className="note xs">발생 이슈</p>
                <b className="stat-num">{open.issueCount}<span className="unit">건</span></b>
                {open.prevIssueCount != null ? (
                  <p className={`note xs ${open.issueCount <= open.prevIssueCount ? 'ok' : 'err'}`}>
                    전주 {open.prevIssueCount} → {Math.abs(Math.round((1 - open.issueCount / open.prevIssueCount) * 100))}%
                    {open.issueCount <= open.prevIssueCount ? ' 감소' : ' 증가'}
                  </p>
                ) : <span className="note xs drill-hint">이슈 보기 →</span>}
              </button>
              <button className="panel tc drill-card" style={sc} onClick={() => openDrill('RESOLVED')}>
                <p className="note xs">해결</p>
                <b className="stat-num" style={{ color: 'var(--mint)' }}>{open.resolvedCount}<span className="unit">건</span></b>
                <p className="note xs">해결률 {Math.round((open.resolvedCount / open.issueCount) * 100)}%</p>
              </button>
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
              <span style={{
                fontSize: 15, fontWeight: 700, color: 'var(--navy)',
                background: '#ffd23f', border: '2px solid var(--navy)',
                padding: '8px 14px', lineHeight: 1.4, display: 'inline-flex', alignItems: 'center', gap: 8,
              }}>
                <span style={{ fontSize: 24 }}>📮</span>
                매주 <b style={{ color: 'var(--navy)' }}>월요일 아침 9시</b>, 이 리포트가 이메일로 자동 발송돼요.
              </span>
              <button className="btn co sm ml-auto" onClick={() => setOpen(null)}>닫기</button>
            </div>
          </div>
        </div>
      )}

      {/* 드릴다운 — 건수 클릭 시 그 주 이슈를 하나하나. 레포가 여러 개면 드롭다운으로 필터 */}
      {drill && (() => {
        const repos = [...new Set(drill.issues.map((i) => String(i.repoName)))] as string[];
        const shown = drillRepo === 'ALL' ? drill.issues : drill.issues.filter((i) => i.repoName === drillRepo);
        return (
        <div className="modal-mask" onClick={() => setDrill(null)}>
          <div className="modal" role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
            <h3>{drill.status === 'RESOLVED' ? '해결된 이슈' : '발생 이슈'} · 지난주</h3>

            {repos.length > 1 && (
              <select className="drill-repo" value={drillRepo} onChange={(e) => setDrillRepo(e.target.value)}>
                <option value="ALL">전체 레포 ({drill.issues.length})</option>
                {repos.map((rn) => (
                  <option key={rn} value={rn}>{rn} ({drill.issues.filter((i) => i.repoName === rn).length})</option>
                ))}
              </select>
            )}

            {shown.length === 0 ? (
              <p className="note" style={{ margin: '14px 0' }}>해당하는 이슈가 없어요.</p>
            ) : (
              <div className="issue-drill">
                {shown.map((it) => (
                  <div key={it.issueId} className="idrow">
                    <div className="idrow-top">
                      <span className={`chip sm ${SEV_CHIP[it.severity] ?? 'gray'}`}>{sevKo(it.severity)}</span>
                      <span className="chip sm navy">{catKo(it.category)}</span>
                      <span className="note xs">{it.repoName} #{it.prNumber}</span>
                      {it.status === 'RESOLVED' && <span className="note xs" style={{ color: 'var(--mint)', fontWeight: 700 }}>✔ 해결</span>}
                      <span className="mono xs ml-auto">{it.filePath}{it.lineNumber != null ? `:${it.lineNumber}` : ''}</span>
                    </div>
                    {brief(it.description) && <p className="idrow-desc">{brief(it.description)}</p>}
                  </div>
                ))}
              </div>
            )}

            <div className="row" style={{ justifyContent: 'flex-end', marginTop: 14 }}>
              <button className="btn wh sm" onClick={() => setDrill(null)}>뒤로</button>
            </div>
          </div>
        </div>
        );
      })()}
    </main>
  );
}
