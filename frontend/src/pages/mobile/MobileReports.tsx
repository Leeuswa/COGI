/*
 * 모바일 전용 주간 리포트.
 * 데스크톱은 한 줄 요약 + 팝업인데, 폰에서는 요약 텍스트가 '…'로 잘리고 팝업도 답답하다.
 * 리뷰 히스토리와 같은 아코디언으로 바꿔 그 자리에서 펼친다.
 * 기능은 데스크톱과 같다 — 조회 전용(발송은 매주 월 배치).
 */
import { useEffect, useState } from "react";
import * as api from "../../api/client";
import { catKo } from "../../data/constants";
import "../../styles/mobile/reports.css";

const fmt = (d) => d.slice(5).replace("-", "."); // '2026-07-06' → '07.06'
const pct = (a, b) => (b > 0 ? Math.round((a / b) * 100) : 0); // 이슈 0건인 주 나눗셈 방어

export default function MobileReports() {
  const [reports, setReports] = useState(null);
  const [openId, setOpenId] = useState(null);

  useEffect(() => { api.getWeeklyReports().then(setReports).catch(() => setReports([])); }, []);

  if (!reports) return <main className="mapp"><p className="mnote">불러오는 중…</p></main>;

  if (reports.length === 0) {
    return (
      <main className="mapp">
        <section className="mcard mempty">
          <p>아직 리포트가 없어요.<br />첫 주가 지나면 여기에 쌓입니다.</p>
        </section>
      </main>
    );
  }

  return (
    <main className="mapp">
      <p className="mlead">매주 월요일 오전, 지난 한 주의 리포트가 메일로도 자동 발송돼요.</p>

      <ul className="mrp">
        {reports.map((r) => {
          const on = openId === r.id;
          const down = r.prevIssueCount != null && r.issueCount <= r.prevIssueCount;
          return (
            <li key={r.id} className={on ? "on" : ""}>
              <button type="button" className="mrp-row" onClick={() => setOpenId(on ? null : r.id)}>
                <span className="mrp-main">
                  <b>{fmt(r.periodStart)} – {fmt(r.periodEnd)}</b>
                  <i>이슈 {r.issueCount} · 해결 {r.resolvedCount}</i>
                </span>
                <span className="mrp-cat">{catKo(r.topCategory)}</span>
                <span className="mrp-caret">{on ? "▲" : "▼"}</span>
              </button>

              {on && (
                <div className="mrp-detail">
                  {/* 요약은 폰에서도 자르지 않는다 — 이 화면의 본문이다 */}
                  <p className="mrp-summary">{r.summary}</p>

                  <div className="mrp-stats">
                    <div>
                      <i>발생 이슈</i>
                      <b>{r.issueCount}</b>
                      {r.prevIssueCount != null && (
                        <em className={down ? "ok" : "no"}>
                          전주 {r.prevIssueCount} → {Math.abs(100 - pct(r.issueCount, r.prevIssueCount))}%
                          {down ? " 감소" : " 증가"}
                        </em>
                      )}
                    </div>
                    <div>
                      <i>해결</i>
                      <b className="ok">{r.resolvedCount}</b>
                      <em>해결률 {pct(r.resolvedCount, r.issueCount)}%</em>
                    </div>
                  </div>

                  <h3 className="mrp-h">카테고리별 발생</h3>
                  <div className="mrp-cats">
                    {r.categories.map((c) => (
                      <div key={c.name} className="mrp-catrow">
                        <span>{catKo(c.name)}</span>
                        <i><em style={{ width: `${pct(c.count, r.issueCount)}%` }} /></i>
                        <b>{c.count}</b>
                      </div>
                    ))}
                  </div>

                  <h3 className="mrp-h">이번 주 학습 활동</h3>
                  <p className="mnote">
                    퀴즈 제출 <b>{r.quizSubmits}회</b> · 정답률 <b>{r.correctRate}%</b>
                    <br />
                    주말 기준 연속 학습 <b className="hot">{r.streakEnd}일</b>
                  </p>

                  <h3 className="mrp-h">코기의 다음 주 추천</h3>
                  <ol className="mrp-actions">
                    {r.actions.map((a, i) => <li key={i}>{a}</li>)}
                  </ol>

                  <p className="mrp-mail">📮 매주 <b>월요일 아침 9시</b>, 이 리포트가 이메일로 자동 발송돼요.</p>
                </div>
              )}
            </li>
          );
        })}
      </ul>
    </main>
  );
}
