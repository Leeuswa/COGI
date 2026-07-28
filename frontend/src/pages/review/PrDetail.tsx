/*
 * PR 리뷰 상세 (RDB-001/003/004, API-025/027/028/031/033/034/035~037)
 * 이 서비스에서 제일 밀도 높은 화면. 이슈 카드 하나에 붙는 것들:
 *   - 코드 스니펫 (문제 라인 하이라이트)
 *   - [의도한 코드] 토글 (FR-40) — 누르면 약점 통계에서 제외된다
 *   - [해결 요청] / [무시 요청] (FR-43) → PENDING → 팀장 [승인]/[반려] (FR-44)
 *     ※ 목 모드에선 한 명이 팀원+팀장 역할을 다 해본다. 실서버는 role로 버튼이 갈린다.
 *   - 상태 배지: OPEN → PENDING → RESOLVED / IGNORED
 * 상단엔 모델 선택(FR-34~35)과 내보내기 — Markdown 다운로드 + 브랜드 PDF 다운로드(html2canvas+jsPDF, 서버 없이 클라이언트에서 생성).
 */
import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import * as api from '../../api/client';
import { useGame } from '../../context/GameContext';
import { PageHead, SevChip, renderDescription } from '../../components/ui';
import { catKo, ISSUE_STATUS_KO, MODEL_TIERS } from '../../data/constants';

// 내보내기(MD)는 백엔드 없이 프론트에서 파일을 만들어 내려준다 (FR-45~46)
function download(text, filename) {
  const url = URL.createObjectURL(new Blob([text], { type: 'text/plain;charset=utf-8' }));
  const a = document.createElement('a');
  a.href = url; a.download = filename; a.click();
  URL.revokeObjectURL(url);
}

// PDF용 한글 폰트(public/fonts) — 한 번 받아 base64로 캐시. jsPDF에 심어 텍스트로 그린다.
let _koreanFontB64: string | null = null;
async function loadKoreanFont(): Promise<string> {
  if (_koreanFontB64) return _koreanFontB64;
  const buf = await (await fetch('/fonts/NanumGothic.ttf')).arrayBuffer();
  const bytes = new Uint8Array(buf);
  let bin = '';
  for (let i = 0; i < bytes.length; i += 0x8000) bin += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  _koreanFontB64 = btoa(bin);
  return _koreanFontB64;
}

export default function PrDetail() {
  const { prId } = useParams();
  const { notify } = useGame();

  const [data, setData] = useState(null);       // { pr, issues }
  const [modelChoice, setModelChoice] = useState(MODEL_TIERS[0].name);
  const [savingModel, setSavingModel] = useState(false);

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
      setModelChoice(res.pr.selectedModel || MODEL_TIERS[0].name);
    });
  }, [prId]);

  // 모델 선택(API-028) — 지금 보이는 결과는 이미 끝난 리뷰라 그대로고, PR에 새 커밋이 올라와
  // 다시 리뷰될 때(webhook synchronize)부터 여기서 고른 모델이 적용된다
  const saveModel = async () => {
    setSavingModel(true);
    try {
      await api.selectPrModel(data.pr.id, modelChoice);
      notify('다음 리뷰부터 이 모델을 쓸게요.');
    } catch (ex) {
      notify(ex.message || '모델 선택에 실패했어요.');
    } finally {
      setSavingModel(false);
    }
  };

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
  const pending = issues.filter((it) => it.status === 'PENDING').length; // 팀장 승인 대기(CRITICAL만 여기로 옴)

  // 이슈 승인 흐름(RDB-003) — 팀장(pr.myRole==='OWNER')만 버튼이 보인다
  const decide = async (issueId, approve) => {
    await api.decideResolveRequest(issueId, approve);
    setData((d) => ({
      ...d,
      issues: d.issues.map((it) => (it.id === issueId ? { ...it, status: approve ? 'RESOLVED' : 'OPEN' } : it)),
    }));
    notify(approve ? '승인했어요. 해결됨으로 반영됐어요.' : '반려했어요. 다시 미해결로 돌아갔어요.');
  };

  // ── 내보내기 공통 라벨 ──
  const STAT_MD = { OPEN: '미해결', PENDING: '승인 대기', RESOLVED: '해결', IGNORED: '무시' };
  const SEV_DOT = { CRITICAL: '🔴', MAJOR: '🟠', MINOR: '🔵' }; // 심각도 하나만 색으로 표시(도배 X)

  // Markdown 정본 — 어느 뷰어에서든 깔끔하게(GitHub 전용 문법 안 씀). 심각도 점 + 요약표 + 이슈별 인용.
  const buildMd = () => {
    const head = [
      `# PR #${pr.githubPrNumber} — ${pr.title}`,
      ``,
      `> 작성자 **${pr.authorName ?? '-'}** · ${(pr.createdAt || '').replace('T', ' ').slice(0, 16)} · COGI Code Guide`,
      ``,
      `## 요약`,
      ``,
      `총 이슈 **${issues.length}건**　|　✅ 해결 **${resolved}**　·　🔧 미해결 **${open_}**　·　⊘ 무시 **${ignored}**`,
      ``,
      `| 구분 | 심각도 | 카테고리 | 위치 | 상태 |`,
      `|:-:|:--|:--|:--|:--|`,
      ...issues.map((it, i) =>
        `| ${i + 1} | ${SEV_DOT[it.severity] ?? '⚪'} \`${it.severity}\` | ${catKo(it.category)} | \`${it.filePath}:${it.lineNumber}\` | ${STAT_MD[it.status] ?? it.status} |`),
      ``,
      `## 상세`,
      ``,
    ];
    const body = issues.flatMap((it, i) => {
      const desc = String(it.description ?? '').split('\n').map((l) => `> ${l}`).join('\n');
      return [
        `### ${SEV_DOT[it.severity] ?? '⚪'} ${i + 1}. ${catKo(it.category)} · \`${it.severity}\``,
        `**위치** \`${it.filePath}:${it.lineNumber}\`　**상태** ${STAT_MD[it.status] ?? it.status}`,
        ``,
        desc,
        ``,
        ...(it.codeSnippet ? ['```', ...it.codeSnippet, '```', ''] : []),
        `---`,
        ``,
      ];
    });
    return [...head, ...body].join('\n').trimEnd() + '\n';
  };
  const exportMd = () => download(buildMd(), `cogi-pr${pr.githubPrNumber}-review.md`);

  // PDF — 오프스크린에 브랜드 리포트를 그려 html2canvas로 캡처 → jsPDF로 A4에 담아 바로 다운로드.
  // 서버 불필요. 이미지 기반이라 한글이 안 깨지고, 프린트 대화상자 없이 파일로 저장됨.
  // PDF — jsPDF에 한글 폰트(NanumGothic)를 심어 텍스트로 직접 그린다.
  // 다운로드 + 한글 정상 + 글자 선택 가능. 페이지나눔은 커서로 직접 제어(카드 안 잘림).
  const SEV_C = { CRITICAL: ['#fceceb', '#a32d2d', '#e24b4a'], MAJOR: ['#faeeda', '#854f0b', '#ba7517'], MINOR: ['#e6f1fb', '#185fa5', '#378add'] };
  const rgb = (h) => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
  const exportPdf = async () => {
    try {
      const { jsPDF } = await import('jspdf');
      const b64 = await loadKoreanFont();
      const pdf = new jsPDF('p', 'mm', 'a4');
      pdf.addFileToVFS('NanumGothic.ttf', b64);
      pdf.addFont('NanumGothic.ttf', 'Nanum', 'normal');
      pdf.setFont('Nanum');

      const PW = 210, PH = 297, M = 14, W = PW - M * 2;
      const fill = (h) => { const c = rgb(h); pdf.setFillColor(c[0], c[1], c[2]); };
      const ink = (h) => { const c = rgb(h); pdf.setTextColor(c[0], c[1], c[2]); };
      let y = M;
      const need = (h) => { if (y + h > PH - M) { pdf.addPage(); y = M; } };

      // 헤더
      fill('#1b2a4a'); pdf.rect(M, y, W, 26, 'F');
      ink('#ffd23f'); pdf.setFontSize(9); pdf.text('COGI · CODE GUIDE', M + 6, y + 8);
      ink('#ffffff'); pdf.setFontSize(15);
      pdf.text(pdf.splitTextToSize(`PR #${pr.githubPrNumber} — ${pr.title}`, W - 12)[0], M + 6, y + 16);
      ink('#aebfe0'); pdf.setFontSize(9);
      pdf.text(`작성자 ${pr.authorName ?? '-'} · ${(pr.createdAt || '').replace('T', ' ').slice(0, 16)}`, M + 6, y + 22);
      y += 32;

      // 스탯 4칸
      const stats = [['총 이슈', issues.length, '#fdfcf7', '#1b2a4a'], ['해결', resolved, '#c8f2df', '#1b7a52'], ['미해결', open_, '#fceceb', '#a32d2d'], ['무시', ignored, '#f1efe8', '#5f5e5a']];
      const gap = 4, bw = (W - gap * 3) / 4, bh = 20;
      stats.forEach((s, i) => {
        const x = M + i * (bw + gap);
        fill(s[2]); pdf.setDrawColor(229, 225, 214); pdf.roundedRect(x, y, bw, bh, 2, 2, 'FD');
        ink(s[3]); pdf.setFontSize(16); pdf.text(String(s[1]), x + bw / 2, y + 10, { align: 'center' });
        ink('#666666'); pdf.setFontSize(8); pdf.text(String(s[0]), x + bw / 2, y + 16, { align: 'center' });
      });
      y += bh + 10;

      // 섹션 타이틀
      fill('#f0704a'); pdf.rect(M, y - 4, 1.6, 6, 'F');
      ink('#1b2a4a'); pdf.setFontSize(12); pdf.text('발견된 이슈', M + 4, y + 1);
      y += 8;

      // 이슈 카드
      issues.forEach((it) => {
        const [bg, fg, bar] = SEV_C[it.severity] || ['#f1efe8', '#444441', '#888780'];
        pdf.setFontSize(10);
        const desc = pdf.splitTextToSize(String(it.description ?? ''), W - 14);
        const cardH = 15 + desc.length * 5 + 3;
        need(cardH + 3);
        pdf.setFillColor(255, 255, 255); pdf.setDrawColor(229, 225, 214);
        pdf.roundedRect(M, y, W, cardH, 2, 2, 'FD');
        fill(bar); pdf.rect(M, y, 1.8, cardH, 'F');
        // 뱃지 줄
        let bx = M + 6; const by = y + 8;
        pdf.setFontSize(8);
        const sw = pdf.getTextWidth(it.severity) + 6;
        fill(bg); pdf.roundedRect(bx, by - 4.2, sw, 6, 1, 1, 'F'); ink(fg); pdf.text(it.severity, bx + 3, by);
        bx += sw + 3;
        const cat = catKo(it.category), cw = pdf.getTextWidth(cat) + 6;
        fill('#1b2a4a'); pdf.roundedRect(bx, by - 4.2, cw, 6, 1, 1, 'F'); ink('#ffffff'); pdf.text(cat, bx + 3, by);
        bx += cw + 4;
        ink('#666666'); pdf.text(String(ISSUE_STATUS_KO[it.status] ?? it.status), bx, by);
        ink('#8b96b5'); pdf.text(`${it.filePath}:${it.lineNumber}`, M + W - 6, by, { align: 'right' });
        // 설명
        ink('#2c2c2a'); pdf.setFontSize(10); pdf.text(desc, M + 6, y + 15);
        y += cardH + 4;
      });

      need(8);
      ink('#b4b2a9'); pdf.setFontSize(8);
      pdf.text('Generated by COGI · Code Guide', PW / 2, y + 2, { align: 'center' });

      pdf.save(`cogi-pr${pr.githubPrNumber}-review.pdf`);
    } catch {
      notify('PDF 생성에 실패했어요. 잠시 후 다시 시도해주세요.');
    }
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

      {/* 다음 리뷰에 쓸 모델 선택 (API-028, FR-34/35) — 지금 보이는 결과엔 영향 없고,
          PR에 새 커밋이 올라와 웹훅이 다시 리뷰를 돌릴 때부터 적용된다 */}
      <div className="panel" style={{ marginBottom: 18 }}>
        <div className="row" style={{ flexWrap: 'wrap', gap: 10, alignItems: 'center' }}>
          <b style={{ fontSize: 14 }}>다음 리뷰에 쓸 모델</b>
          <select value={modelChoice} onChange={(e) => setModelChoice(e.target.value)}>
            {MODEL_TIERS.map((m) => (
              <option key={m.name} value={m.name}>{m.label}</option>
            ))}
          </select>
          <button className="btn co sm" disabled={savingModel} onClick={saveModel}>
            {savingModel ? '저장 중…' : '저장'}
          </button>
          <span className="note xs">새 커밋으로 다시 리뷰될 때부터 적용돼요. 지금 결과는 그대로예요.</span>
        </div>
      </div>

      {/* 최종 확인 요약 — 판정과 리뷰는 스튜디오에서 끝났고, 여기선 정리된 결과를 본다 (#5~7) */}
      <div className="panel" style={{ marginBottom: 18 }}>
        <div className="row" style={{ flexWrap: 'wrap', gap: 18, alignItems: 'center' }}>
          <b style={{ fontSize: 15 }}>최종 검토 결과</b>
          <span className="chip low">해결 {resolved}</span>
          <span className="chip gray">무시 {ignored}</span>
          {pending > 0 && <span className="chip mid">승인 대기 {pending}</span>}
          {open_ > 0 && <span className="chip hi">미판정 {open_}</span>}
          <span className="ml-auto" style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            {open_ > 0 && (
              <Link className="btn co sm" to="/app/paste" state={{ prId: pr.id }}>🎬 스튜디오에서 마저 판정</Link>
            )}
            <button className="btn wh sm" onClick={exportMd}>⬇ Markdown</button>
            <button className="btn wh sm" onClick={exportPdf}>⬇ PDF</button>
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

          {/* 스튜디오에서 내린 판정을 '확인만' 한다 — 결정은 스튜디오에서 끝났다 (#5/#7).
              단, PENDING(팀장 승인 대기)은 여기서 팀장이 직접 승인/반려한다 (FR-44, RDB-003) */}
          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'center' }}>
            <span className="note sm">
              {it.status === 'RESOLVED' ? '✔ 고치기로 확정한 지적이에요'
                : it.status === 'IGNORED' ? '🙋 의도한 코드로 확인했어요 (통계 제외)'
                : it.status === 'PENDING' ? '⏳ 심각도 높은 이슈라 팀장 승인을 기다리는 중이에요'
                : '스튜디오에서 아직 판정하지 않은 지적이에요'}
            </span>
            {it.status === 'PENDING' && pr.myRole === 'OWNER' && (
              <span className="row ml-auto" style={{ gap: 8 }}>
                <button className="btn wh sm" onClick={() => decide(it.id, false)}>반려</button>
                <button className="btn co sm" onClick={() => decide(it.id, true)}>승인</button>
              </span>
            )}
            {!(it.status === 'PENDING' && pr.myRole === 'OWNER') && (
              <Link className="btn wh sm ml-auto" to="/app/weakness">이 유형 약점 보기 →</Link>
            )}
          </div>
        </div>
      ))}
    </main>
  );
}
