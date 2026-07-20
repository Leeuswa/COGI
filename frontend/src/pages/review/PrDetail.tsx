/*
 * PR 리뷰 상세 (RDB-001/003/004, API-025/027/028/031/033/034/035~037)
 * 이 서비스에서 제일 밀도 높은 화면. 이슈 카드 하나에 붙는 것들:
 *   - 코드 스니펫 (문제 라인 하이라이트)
 *   - [의도한 코드] 토글 (FR-40) — 누르면 약점 통계에서 제외된다
 *   - [해결 요청] / [무시 요청] (FR-43) → PENDING → 팀장 [승인]/[반려] (FR-44)
 *     ※ 목 모드에선 한 명이 팀원+팀장 역할을 다 해본다. 실서버는 role로 버튼이 갈린다.
 *   - 상태 배지: OPEN → PENDING → RESOLVED / IGNORED
 * 상단엔 모델 선택(FR-34~35)과 내보내기(MD/TXT 실동작, Notion/PDF는 MVP 이후).
 */
import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import * as api from '../../api/client';
import { useGame } from '../../context/GameContext';
import { PageHead, SevChip, renderDescription } from '../../components/ui';
import { catKo, ISSUE_STATUS_KO } from '../../data/constants';

// MD/TXT 내보내기는 백엔드 없이 프론트에서 파일을 만들어 내려준다 (FR-45~46)
function download(text, filename) {
  const url = URL.createObjectURL(new Blob([text], { type: 'text/plain;charset=utf-8' }));
  const a = document.createElement('a');
  a.href = url; a.download = filename; a.click();
  URL.revokeObjectURL(url);
}

export default function PrDetail() {
  const { prId } = useParams();
  const { notify } = useGame();

  const [data, setData] = useState(null);       // { pr, issues }

  useEffect(() => {
    api.getPrReview(prId).then((res) => {
      // 스튜디오에서 내린 판정(무시/해결)을 확인 화면에 반영 (#5/#7)
      let verdicts = {};
      try { verdicts = JSON.parse(sessionStorage.getItem('cogi-verdicts') || '{}'); } catch { /* 무시 */ }
      const issues = res.issues.map((it) => {
        const v = verdicts[it.id];
        return v ? { ...it, status: v, acknowledged: v === 'IGNORED' } : it;
      });
      setData({ ...res, issues });
    });
  }, [prId]);

  if (!data) {
    return (
      <main className="app-main">
        <div className="panel"><p className="note">리뷰를 불러오는 중…</p></div>
      </main>
    );
  }

  const { pr, issues } = data;
  const resolved = issues.filter((it) => it.status === 'RESOLVED').length;
  const ignored = issues.filter((it) => it.status === 'IGNORED').length;
  const open_ = issues.filter((it) => it.status === 'OPEN').length;

  // 내보내기 본문 조립. 마크다운 하나 만들어두고 TXT는 기호만 벗긴다
  const buildMd = () =>
    [
      `# PR #${pr.githubPrNumber} — ${pr.title}`,
      `> 작성자 ${pr.authorName} · ${pr.createdAt} · COGI 리뷰 리포트`,
      '',
      ...issues.flatMap((it) => [
        `## [${it.severity}] ${it.category} — ${it.filePath}:${it.lineNumber}`,
        `- 상태: ${it.status}${it.acknowledged ? ' (의도한 코드 — 통계 제외)' : ''}`,
        '', it.description, '',
        ...(it.codeSnippet ? ['```', ...it.codeSnippet, '```', ''] : []),
      ]),
    ].join('\n');

  const exportAs = async (fmt) => {
    await api.exportReview(pr.id, fmt); // 실서버에선 여기서 파일이 온다
    const md = buildMd();
    if (fmt === 'MD') download(md, `cogi-pr${pr.githubPrNumber}-review.md`);
    else download(md.replace(/[#>`]/g, ''), `cogi-pr${pr.githubPrNumber}-review.txt`);
  };

  const statusChip = (s) =>
    ({ OPEN: 'hi', PENDING: 'mid', RESOLVED: 'low', IGNORED: 'gray' }[s] || 'gray');

  return (
    <main className="app-main">
      <PageHead
        badge={`PR #${pr.githubPrNumber}`}
        title={pr.title}
        lead={`${pr.authorName} · ${pr.createdAt.replace('T', ' ')} · 스튜디오에서 검토를 마친 결과를 확인하는 곳이에요`}
      />

      {/* 최종 확인 요약 — 판정과 리뷰는 스튜디오에서 끝났고, 여기선 정리된 결과를 본다 (#5~7) */}
      <div className="panel" style={{ marginBottom: 18 }}>
        <div className="row" style={{ flexWrap: 'wrap', gap: 18, alignItems: 'center' }}>
          <b style={{ fontSize: 15 }}>최종 검토 결과</b>
          <span className="chip low">해결 {resolved}</span>
          <span className="chip gray">무시 {ignored}</span>
          {open_ > 0 && <span className="chip hi">미판정 {open_}</span>}
          <span className="ml-auto" style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            {open_ > 0 && (
              <Link className="btn co sm" to="/app/paste" state={{ prId: pr.id }}>🎬 스튜디오에서 마저 판정</Link>
            )}
            <button className="btn wh sm" onClick={() => exportAs('MD')}>⬇ Markdown</button>
            <button className="btn wh sm" onClick={() => exportAs('TXT')}>⬇ TXT</button>
            <button className="btn wh sm" onClick={async () => {
              const r = await api.exportNotion(pr.id, null);
              r.notionPageUrl ? window.open(r.notionPageUrl) : notify('Notion 내보내기는 준비 중이에요 (MVP 이후)');
            }}>Notion</button>
            <button className="btn wh sm" onClick={async () => {
              const r = await api.exportPdf(pr.id);
              r.pdfUrl ? window.open(r.pdfUrl) : notify('PDF 내보내기는 준비 중이에요 (MVP 이후)');
            }}>PDF</button>
          </span>
        </div>
      </div>

      {issues.map((it) => (
        <div key={it.id} className="panel">
          {/* 이슈 헤더 */}
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap', marginBottom: 12 }}>
            <SevChip sev={it.severity} />
            <span className="chip navy">{catKo(it.category)}</span>
            <span className={`chip ${statusChip(it.status)}`}>{ISSUE_STATUS_KO[it.status] ?? it.status}</span>
            {/* acknowledged는 항상 IGNORED와 같이 감(무시됨=통계 제외) — 같은 회색 칩을 또 붙이면 중복돼 보여서 텍스트로만 부연 */}
            {it.acknowledged && <span className="note xs">(통계 제외)</span>}
            <span className="mono" style={{ fontSize: 12, color: 'var(--sub)', marginLeft: 'auto' }}>
              {it.filePath}:{it.lineNumber}
            </span>
          </div>

          {/* 코드 스니펫 — 문제 라인만 배경 하이라이트. PR diff 기반 리뷰는 스니펫을 따로 안 남겨서 없을 수 있음 */}
          {it.codeSnippet && (
            <pre className="codebox">
              {it.codeSnippet.map((line, i) => {
                const isHit = line.trimStart().startsWith(String(it.lineNumber) + ' ');
                return <span key={i} className={isHit ? 'hl-line' : undefined}>{line + '\n'}</span>;
              })}
            </pre>
          )}

          <div style={{ fontSize: 13.5, lineHeight: 1.95, margin: '14px 0' }}>{renderDescription(it.description)}</div>

          {/* 스튜디오에서 내린 판정을 '확인만' 한다 — 결정은 스튜디오에서 끝났다 (#5/#7) */}
          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'center' }}>
            <span className="note sm">
              {it.status === 'RESOLVED' ? '✔ 고치기로 확정한 지적이에요'
                : it.status === 'IGNORED' ? '🙋 의도한 코드로 확인했어요 (통계 제외)'
                : '스튜디오에서 아직 판정하지 않은 지적이에요'}
            </span>
            <Link className="btn wh sm ml-auto" to="/app/weakness">이 유형 약점 보기 →</Link>
          </div>
        </div>
      ))}
    </main>
  );
}
