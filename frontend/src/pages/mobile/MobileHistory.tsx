/*
 * 모바일 전용 리뷰 히스토리.
 * 데스크톱은 6열 표 + 팝업인데, 폰에서는 표가 잘리고 팝업도 답답하다.
 * 카드 리스트로 바꾸고 상세는 카드를 눌러 그 자리에서 펼친다(아코디언).
 */
import { useEffect, useState } from "react";
import * as api from "../../api/client";
import { renderDescription } from "../../components/ui";
import { catKo, ISSUE_STATUS_KO, REVIEW_TARGET_KO, PROCESS_STATUS_KO, sevKo } from "../../data/constants";
import "../../styles/mobile/history.css";

// "2026-07-27T17:16:13" → "07.27 17:16"
const when = (iso: string) => (iso ? `${iso.slice(5, 10).replace("-", ".")} ${iso.slice(11, 16)}` : "-");
const label = (r) => r.originalFilename ?? (REVIEW_TARGET_KO[r.targetType] ?? r.targetType);

export default function MobileHistory() {
  const [items, setItems] = useState(null);
  const [openId, setOpenId] = useState(null);
  const [detail, setDetail] = useState(null);

  useEffect(() => { api.getReviewHistory().then(setItems).catch(() => setItems([])); }, []);

  // 같은 카드를 다시 누르면 접는다
  const toggle = (r) => {
    if (openId === r.reviewId) return setOpenId(null);
    setOpenId(r.reviewId);
    setDetail(null);
    api.getReviewHistoryDetail(r.reviewId).then(setDetail).catch(() => setDetail({ issues: [] }));
  };

  if (!items) return <main className="mapp"><p className="mnote">불러오는 중…</p></main>;

  if (items.length === 0) {
    return (
      <main className="mapp">
        <section className="mcard mempty">
          <p>아직 리뷰 기록이 없어요.<br />스튜디오에서 코드를 리뷰해 보세요.</p>
        </section>
      </main>
    );
  }

  return (
    <main className="mapp">
      <ul className="mhist">
        {items.map((r) => {
          const on = openId === r.reviewId;
          return (
            <li key={r.reviewId} className={on ? "on" : ""}>
              <button type="button" className="mh-row" onClick={() => toggle(r)}>
                <span className="mh-main">
                  <b>{label(r)}</b>
                  <i>{when(r.createdAt)} · 이슈 {r.issueCount}건</i>
                </span>
                {r.topSeverity && <span className={`mh-sev ${r.topSeverity.toLowerCase()}`}>{sevKo(r.topSeverity)}</span>}
                <span className="mh-caret">{on ? "▲" : "▼"}</span>
              </button>

              {on && (
                <div className="mh-detail">
                  <div className="mh-meta">
                    <span>{r.modelName}</span>
                    <span className="dot">·</span>
                    <span>{PROCESS_STATUS_KO[r.status] ?? r.status}</span>
                  </div>

                  {!detail ? (
                    <p className="mnote">불러오는 중…</p>
                  ) : detail.issues?.length === 0 ? (
                    <p className="mnote">지적된 내용이 없어요.</p>
                  ) : (
                    detail.issues.map((it) => (
                      <div key={it.id} className="mh-issue">
                        <div className="mh-tags">
                          <span className={`mh-sev ${it.severity.toLowerCase()}`}>{sevKo(it.severity)}</span>
                          <span className="mh-cat">{catKo(it.category)}</span>
                          <span className="mh-st">{ISSUE_STATUS_KO[it.status] ?? it.status}</span>
                        </div>
                        {it.filePath && <p className="mh-file">{it.filePath}{it.lineNumber ? `:${it.lineNumber}` : ""}</p>}
                        <div className="mh-desc">{renderDescription(it.description)}</div>
                      </div>
                    ))
                  )}

                  {/* 후속 질문 기록(2026-07-29) */}
                  {detail?.questions?.length > 0 && (
                    <div className="mh-questions">
                      <p className="mnote" style={{ marginTop: 12, marginBottom: 6 }}>💬 후속 질문 {detail.questions.length}건</p>
                      {detail.questions.map((q, i) => (
                        <div key={i} className="mh-issue">
                          <p className="mh-file">Q. {q.question}</p>
                          <div className="mh-desc">{renderDescription(q.answer)}</div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </li>
          );
        })}
      </ul>
    </main>
  );
}
