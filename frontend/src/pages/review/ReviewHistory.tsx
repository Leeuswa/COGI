/*
 * 리뷰 히스토리 [설계 추론] — 스튜디오에서 만든 개별 리뷰(붙여넣기/업로드/PR 가져오기)를 다시 찾아보는 화면.
 * "약점 통계"는 3회 이상 반복된 카테고리만 집계라 개별 리뷰 단위 조회가 안 됐다 — 그 갭을 메운다.
 */
import { useEffect, useState } from 'react';
import * as api from '../../api/client';
import { PageHead, SevChip, renderDescription } from '../../components/ui';
import { catKo, ISSUE_STATUS_KO, REVIEW_TARGET_KO, PROCESS_STATUS_KO } from '../../data/constants';
import useIsMobile from '../../hooks/useIsMobile';
import MobileHistory from '../mobile/MobileHistory';

const fmt = (iso) => iso?.replace('T', ' ').slice(0, 16);

const label = (r) => r.originalFilename ?? (REVIEW_TARGET_KO[r.targetType] ?? r.targetType);

// 폰이면 데스크톱 본체를 아예 mount하지 않는다.
// 한 컴포넌트 안에서 갈랐더니 조건부 return 위의 useEffect가 그대로 돌아 같은 API를 두 번 때렸다.
// getWeaknessStats처럼 조회할 때마다 통계를 지우고 다시 넣는 API는 두 번 겹치면 한쪽이 빈 목록을 받는다.
export default function ReviewHistory() {
  return useIsMobile() ? <MobileHistory /> : <DesktopReviewHistory />;
}

function DesktopReviewHistory() {
  const [items, setItems] = useState(null);
  const [open, setOpen] = useState(null);   // 상세 팝업에 띄운 리뷰 요약(reviewId 등)
  const [detail, setDetail] = useState(null); // 팝업에 채울 상세(이슈 목록)

  useEffect(() => { api.getReviewHistory().then(setItems); }, []);

  const openDetail = (item) => {
    setOpen(item);
    setDetail(null);
    api.getReviewHistoryDetail(item.reviewId).then(setDetail);
  };


  return (
    <main className="app-main">
      <PageHead badge="HISTORY" title="리뷰 히스토리"
        lead={"붙여넣기·파일 업로드·PR 가져오기로 만든 리뷰를 모아봐요.\n행을 누르면 그때 나온 지적을 다시 볼 수 있어요."} />

      {!items ? (
        <div className="panel"><p className="note">불러오는 중…</p></div>
      ) : items.length === 0 ? (
        <div className="empty"><p>아직 리뷰 기록이 없어요. 리뷰 스튜디오에서 코드를 리뷰해보세요.</p></div>
      ) : (
        <div className="panel flush">
          <table className="tbl">
            <thead>
              <tr>
                <th>대상</th><th>모델</th><th>이슈</th><th>심각도</th><th>상태</th><th>생성일</th>
              </tr>
            </thead>
            <tbody>
              {items.map((r) => (
                <tr key={r.reviewId} style={{ cursor: 'pointer' }} onClick={() => openDetail(r)}>
                  <td>{label(r)}</td>
                  <td className="mono xs">{r.modelName}</td>
                  <td className="mono">{r.issueCount}</td>
                  <td>{r.topSeverity ? <SevChip sev={r.topSeverity} /> : <span className="note sm">-</span>}</td>
                  <td><span className={`chip ${r.status === 'COMPLETED' ? 'low' : r.status === 'FAILED' ? 'hi' : 'gray'}`}>{PROCESS_STATUS_KO[r.status] ?? r.status}</span></td>
                  <td className="mono xs">{fmt(r.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* 상세 팝업 — 그때 리뷰에서 나온 이슈 목록 */}
      {open && (
        <div className="modal-mask" onClick={() => setOpen(null)}>
          <div className="modal wide" role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
            <h3>📄 {label(open)} — {fmt(open.createdAt)}</h3>

            {!detail ? (
              <p className="note">불러오는 중…</p>
            ) : detail.issues.length === 0 ? (
              <p className="note" style={{ marginBottom: 16 }}>이슈가 없었어요 (분석 불가한 입력이었거나, 지적 사항이 없었어요).</p>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 14, marginBottom: 4 }}>
                {detail.issues.map((it) => (
                  <div key={it.id} className="panel">
                    <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap', marginBottom: 10 }}>
                      <SevChip sev={it.severity} />
                      <span className="chip navy">{catKo(it.category)}</span>
                      <span className="chip gray">{ISSUE_STATUS_KO[it.status] ?? it.status}</span>
                      {/* acknowledged는 항상 IGNORED와 같이 감(무시됨=통계 제외) — 같은 회색 칩을 또 붙이면 중복돼 보여서 텍스트로만 부연 */}
                      {it.acknowledged && <span className="note xs">(통계 제외)</span>}
                      {it.filePath && (
                        <span className="mono" style={{ fontSize: 12, color: 'var(--sub)', marginLeft: 'auto' }}>
                          {it.filePath}{it.lineNumber ? `:${it.lineNumber}` : ''}
                        </span>
                      )}
                    </div>
                    <div style={{ fontSize: 13.5, lineHeight: 1.9 }}>{renderDescription(it.description)}</div>
                  </div>
                ))}
              </div>
            )}

            {/* 후속 질문 기록(2026-07-29) — 예전엔 탭을 닫으면 사라졌던 질문/답변을 여기서 다시 볼 수 있다 */}
            {detail && detail.questions?.length > 0 && (
              <div style={{ marginTop: 20 }}>
                <h4 style={{ marginBottom: 10 }}>💬 후속 질문 {detail.questions.length}건</h4>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                  {detail.questions.map((q, i) => (
                    <div key={i} className="panel" style={{ fontSize: 13.5 }}>
                      <div style={{ marginBottom: 8 }}>
                        <span className="chip navy" style={{ marginRight: 8 }}>질문</span>
                        {q.question}
                      </div>
                      <div>
                        <span className="chip gray" style={{ marginRight: 8 }}>코기</span>
                        {renderDescription(q.answer)}
                      </div>
                      <div className="note xs" style={{ marginTop: 8 }}>{fmt(q.createdAt)}</div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            <div className="row" style={{ marginTop: 20 }}>
              <button className="btn wh sm" onClick={() => setOpen(null)}>닫기</button>
            </div>
          </div>
        </div>
      )}
    </main>
  );
}
