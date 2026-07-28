/*
 * 모바일 전용 학습카드 상세.
 * 데스크톱은 세로로 아주 긴 한 장인데 폰에서는 끝없이 스크롤해야 한다.
 * 개념·문제·계획·복습을 탭으로 나눠 한 번에 하나만 본다.
 * 기능은 데스크톱과 같다 — 북마크·완료·퀴즈 생성/제출·학습계획·지난 문제·캘린더 등록.
 */
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import * as api from "../../api/client";
import { useAuth } from "../../context/AuthContext";
import { useGame } from "../../context/GameContext";
import { QUIZ_TYPES, MODEL_TIERS, PLAN_TIER, catKo } from "../../data/constants";
import { renderCardText, renderQuestion } from "../../components/cardText";
import "../../styles/mobile/card-detail.css";

const gradeOf = (n) => (n >= 8 ? "GREEN_PLUS" : n >= 7 ? "GREEN" : n >= 3 ? "YELLOW" : "RED");
const typeKo = (v) => QUIZ_TYPES.find(([c]) => c === v)?.[1] ?? v;
const modelWeight = (v) => MODEL_TIERS.find((m) => m.name === v)?.tier ?? 1;
const PLAN_DAYS = [3, 5, 7, 14];
const WEEKDAY = ["일", "월", "화", "수", "목", "금", "토"];

const dateAfter = (days) => {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
};
const labelDate = (days) => {
  const d = new Date();
  d.setDate(d.getDate() + days);
  const tag = days === 0 ? " · 오늘" : days === 1 ? " · 내일" : "";
  return `${d.getMonth() + 1}월 ${d.getDate()}일 (${WEEKDAY[d.getDay()]})${tag}`;
};

const TABS: [string, string][] = [["concept", "개념"], ["quiz", "문제"], ["plan", "계획"], ["past", "복습"]];

export default function MobileCardDetail() {
  const { cardId } = useParams();
  const { user } = useAuth();
  const { spendCredit, recordSubmit, earnCoins, notify } = useGame();

  const [tab, setTab] = useState("concept");
  const [card, setCard] = useState(null);
  const [quiz, setQuiz] = useState(null);
  const [picked, setPicked] = useState(null);
  const [result, setResult] = useState(null);
  const [busy, setBusy] = useState(false);
  const [quizType, setQuizType] = useState("MULTIPLE_CHOICE");
  const [model, setModel] = useState(MODEL_TIERS[0].name);
  const [planning, setPlanning] = useState(false);
  const [planDays, setPlanDays] = useState(7);
  const [past, setPast] = useState(null);

  useEffect(() => { api.getLearningCard(cardId).then(setCard); }, [cardId]);

  // 복습 탭을 처음 열 때만 불러온다
  useEffect(() => {
    if (tab === "past" && past === null && card) api.getCardSubmissions(card.id).then(setPast);
  }, [tab, past, card]);

  if (!card) return <main className="mapp"><p className="mnote">카드를 펼치는 중…</p></main>;

  const myTier = PLAN_TIER[user?.planName] ?? 1;
  const plan = card.studyPlan ?? null;

  const makeQuiz = async () => {
    if (busy) return;
    if (!spendCredit(modelWeight(model))) return;
    setBusy(true);
    setResult(null); setPicked(null);
    try { setQuiz(await api.createQuiz(card.id, quizType, model)); }
    catch (e) { notify(e.message || "문제를 만들지 못했어요"); } // 안 잡으면 콘솔에만 뜨고 화면은 그대로다
    finally { setBusy(false); }
  };

  const submit = async () => {
    if (!picked?.toString().trim() || busy) return;
    setBusy(true);
    try {
      const res = await api.submitQuiz(card.id, quiz.id, picked);
      setResult(res);
      recordSubmit();
      earnCoins(res.isCorrect ? 20 : 5);
      notify(res.isCorrect ? "정답! +20코인" : "오답이지만 제출은 인정 +5코인");
      if (res.isCorrect) {
        const n = card.correctCount + 1;
        setCard((c) => ({ ...c, correctCount: n, grade: gradeOf(n) }));
      }
      setPast(null); // 복습 탭을 다시 열면 새로 받는다
    } finally { setBusy(false); }
  };

  const makePlan = async () => {
    if (planning) return;
    if (!spendCredit(1)) return;
    setPlanning(true);
    try {
      const updated = await api.createStudyPlan(card.id, planDays);
      setCard((c) => ({ ...c, studyPlan: updated.studyPlan }));
    } catch (e) { notify(e.message || "계획을 짜지 못했어요"); } // 퀴즈와 같은 이유
    finally { setPlanning(false); }
  };

  // 계획 전체를 한 번에 캘린더로 — 단계마다 누르지 않아도 되게 .ics를 한 번에 내려준다
  const exportPlan = () => {
    plan.forEach((s) => api.downloadIcs(card, dateAfter(s.dayOffset)));
    notify(`${plan.length}개 일정을 캘린더 파일로 내려받았어요`);
  };

  const toggleBm = async () => {
    await api.toggleBookmark(card.id, !card.isBookmarked);
    setCard((c) => ({ ...c, isBookmarked: !c.isBookmarked }));
  };
  const toggleDone = async () => {
    await api.toggleComplete(card.id, !card.isCompleted);
    setCard((c) => ({ ...c, isCompleted: !c.isCompleted }));
  };

  return (
    <main className="mapp mcd">
      {/* 카드 머리 — 등급 진행과 토글 */}
      <section className="mcard mcd-head">
        <b className="mcd-cat">{catKo(card.category)}</b>
        <p className="mcd-meta">{card.language} · {card.level} · 정답 {card.correctCount}회</p>
        <div className="mcd-bar"><i style={{ width: `${Math.min(100, (card.correctCount / 8) * 100)}%` }} /></div>
        <div className="mcd-toggles">
          <button className={`mcd-tg ${card.isBookmarked ? "on" : ""}`} onClick={toggleBm}>
            {card.isBookmarked ? "★ 북마크" : "☆ 북마크"}
          </button>
          <button className={`mcd-tg ${card.isCompleted ? "on" : ""}`} onClick={toggleDone}>
            {card.isCompleted ? "✔ 완료됨" : "완료 표시"}
          </button>
        </div>
      </section>

      {/* 탭 — 긴 화면을 넷으로 쪼갠다 */}
      <div className="mseg mcd-seg">
        {TABS.map(([k, label]) => (
          <button key={k} className={tab === k ? "on" : ""} onClick={() => setTab(k)}>{label}</button>
        ))}
      </div>

      {/* ── 개념 ── */}
      {tab === "concept" && (
        <>
          <section className="mcard">
            <div className="mcard-head"><h2>📖 개념</h2></div>
            <p className="mcd-body">{renderCardText(card.conceptContent)}</p>
            {card.whyItMatters && (
              <>
                <div className="mcard-head mcd-sub"><h2>🔥 그냥 두면 생기는 일</h2></div>
                <p className="mcd-body">{renderCardText(card.whyItMatters)}</p>
              </>
            )}
          </section>

          {/* 기존/추천 코드를 한 카드에 위아래로 — 폰에서 따로 떨어지면 비교가 안 된다 */}
          <section className="mcard mcd-diff">
            <div className="mcd-code bad">
              <span className="mcd-tag">기존 코드</span>
              <pre className="codebox">{card.badExample}</pre>
            </div>
            <div className="mcd-arrow">↓</div>
            <div className="mcd-code good">
              <span className="mcd-tag">추천 코드</span>
              <pre className="codebox">{card.goodExample}</pre>
            </div>
            {card.diffExplain && <p className="mcd-diffnote">{renderCardText(card.diffExplain)}</p>}
          </section>

          {[["🎯 핵심 3줄", card.keyPoints], ["⚠️ 자주 하는 실수", card.pitfalls], ["✅ 리뷰 전 셀프 체크", card.selfCheck]]
            .filter(([, arr]) => arr?.length > 0)
            .map(([title, arr]) => (
              <section key={title as string} className="mcard">
                <div className="mcard-head"><h2>{title}</h2></div>
                <ol className="mcd-points">
                  {(arr as string[]).map((v, i) => <li key={i}>{renderCardText(v)}</li>)}
                </ol>
              </section>
            ))}
        </>
      )}

      {/* ── 문제 ── */}
      {tab === "quiz" && (
        <section className="mcard">
          {!quiz ? (
            <>
              <p className="mnote">유형과 모델을 고르면 문제를 만들어드려요.</p>
              <select className="msel mcd-sel" value={quizType} onChange={(e) => setQuizType(e.target.value)}>
                {QUIZ_TYPES.map(([v, ko]) => <option key={v} value={v}>{ko}</option>)}
              </select>
              <select className="msel mcd-sel" value={model} onChange={(e) => setModel(e.target.value)}>
                {MODEL_TIERS.map((m) => (
                  <option key={m.name} value={m.name} disabled={m.tier > myTier}>
                    {m.label}{m.tier > myTier ? " (상위 플랜)" : ""}
                  </option>
                ))}
              </select>
              <button className="btn co sm full mcd-sel" onClick={makeQuiz} disabled={busy}>
                {busy ? "문제 만드는 중…" : `문제 생성 (⚡${modelWeight(model)})`}
              </button>
            </>
          ) : (
            <>
              <span className="mcd-qtype">{typeKo(quiz.questionType)}</span>
              <div className="mcd-q">{renderQuestion(quiz.question)}</div>

              {quiz.options ? (
                <div className="mcd-picks">
                  {quiz.options.map((op) => {
                    const state = !result ? "" : op === quiz.answer ? "ok" : op === picked ? "miss" : "";
                    return (
                      <button key={op}
                        className={`mcd-pick ${picked === op && !result ? "sel" : ""} ${state}`}
                        disabled={!!result} onClick={() => setPicked(op)}>
                        {op}
                      </button>
                    );
                  })}
                </div>
              ) : (
                <input className="msel mcd-sel" value={picked ?? ""} disabled={!!result}
                  placeholder="정답을 입력하세요"
                  onChange={(e) => setPicked(e.target.value)}
                  onKeyDown={(e) => e.key === "Enter" && submit()} />
              )}

              {!result ? (
                <button className="btn co sm full mcd-sel" onClick={submit} disabled={!picked?.toString().trim() || busy}>
                  제출
                </button>
              ) : (
                <>
                  <p className={`mcd-verdict ${result.isCorrect ? "ok" : "no"}`}>
                    {result.isCorrect ? "정답이에요" : `오답 · 정답은 "${quiz.answer}"`}
                  </p>
                  {quiz.explain && <div className="mcd-explain">{renderQuestion(quiz.explain)}</div>}
                  <button className="btn co sm full mcd-sel" onClick={makeQuiz} disabled={busy}>
                    다른 문제 (⚡{modelWeight(model)})
                  </button>
                  <button className="btn wh sm full mcd-sel" onClick={() => { setQuiz(null); setResult(null); setPicked(null); }}>
                    유형·모델 바꾸기
                  </button>
                </>
              )}
            </>
          )}
        </section>
      )}

      {/* ── 계획 ── */}
      {tab === "plan" && (
        <section className="mcard">
          <p className="mnote">기간을 고르면 그에 맞춰 계획을 짜드려요.</p>
          <div className="mseg mcd-days">
            {PLAN_DAYS.map((d) => (
              <button key={d} className={planDays === d ? "on" : ""} onClick={() => setPlanDays(d)}>{d}일</button>
            ))}
          </div>
          <button className="btn co sm full mcd-sel" onClick={makePlan} disabled={planning}>
            {planning ? "계획 짜는 중…" : plan ? `${planDays}일로 다시 생성 (⚡1)` : "학습 계획 생성 (⚡1)"}
          </button>

          {plan && (
            <ol className="mcd-plan">
              {plan.map((s, i) => (
                <li key={i}>
                  <div className="mp-head">
                    <span className="mp-no">{i + 1}</span>
                    <b>{s.title}</b>
                    <span className="mp-min">{s.minutes}분</span>
                  </div>
                  <p className="mp-date">{labelDate(s.dayOffset)}</p>
                  <p className="mp-focus">{s.focus}</p>
                  {s.resource && <p className="mp-res">📚 {s.resource}</p>}
                  <p className="mp-check">✔ {s.checkpoint}</p>
                  {/* 캘린더 3종은 데스크톱과 같다 — 폰에서는 한 줄에 셋으로 나눈다 */}
                  <div className="mp-cal">
                    <a className="btn wh sm" href={api.googleCalendarUrl(card, dateAfter(s.dayOffset))}
                      target="_blank" rel="noreferrer">구글</a>
                    <button className="btn wh sm" onClick={() => api.downloadIcs(card, dateAfter(s.dayOffset))}>
                      애플
                    </button>
                    <button className="btn wh sm" onClick={async () => {
                      await api.kakaoCalendarAdd(card, dateAfter(s.dayOffset));
                      notify("카카오 캘린더는 비즈니스 인증 후 열려요 (준비 중)");
                    }}>카카오</button>
                  </div>
                </li>
              ))}
            </ol>
          )}

          {/* 단계마다 누르지 않아도 되게 — 데스크톱의 [전체 일정 한 번에 등록]과 같다 */}
          {plan && (
            <div className="mcd-planfoot">
              <p>총 {plan.length}단계 · {plan.reduce((sum, s) => sum + (s.minutes ?? 0), 0)}분</p>
              <button className="btn co sm full" onClick={exportPlan}>📅 전체 일정 한 번에 등록</button>
            </div>
          )}
        </section>
      )}

      {/* ── 복습 ── */}
      {tab === "past" && (
        <section className="mcard">
          {past === null ? (
            <p className="mnote">불러오는 중…</p>
          ) : past.length === 0 ? (
            <p className="mnote">아직 푼 문제가 없어요.<br />문제 탭에서 한 번 풀어보세요.</p>
          ) : (
            past.map((p, i) => (
              <div key={i} className="mcd-log">
                <div className="mcd-logtop">
                  <span className="mcd-qtype">{typeKo(p.questionType)}</span>
                  <span className={`mcd-mark ${p.isCorrect ? "ok" : "no"}`}>{p.isCorrect ? "정답" : "오답"}</span>
                  <span className="mcd-when">{p.submittedAt}</span>
                </div>
                <div className="mcd-q">{renderQuestion(p.question)}</div>
                <p className="mcd-ans">내 답 <b>{p.submittedAnswer || "—"}</b> · 정답 <b>{p.answer}</b></p>
                {p.explain && <div className="mcd-explain">{renderQuestion(p.explain)}</div>}
              </div>
            ))
          )}
        </section>
      )}
    </main>
  );
}
