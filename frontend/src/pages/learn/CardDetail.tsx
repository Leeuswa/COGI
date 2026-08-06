/*
 * 학습카드 상세 (LRN-002, API-044~046 + 지난 문제 복습 + AI 학습 계획)
 * 카드 한 장에 학습 루프가 다 들어있다:
 *   개념 → 예제 → 퀴즈 생성(모델 선택 + 크레딧) → 제출(streak, 정답 무관) → 정답이면 승급 카운트
 *   RED → YELLOW(정답 3) → GREEN(정답 7) → GREEN_PLUS(재도전 8) (FR-61)
 * + 북마크/완료 토글, 승급 진행바(FR-63), 지난 문제 복습(내 답·정답·해설)
 * + AI 학습 계획: 등급·누적 정답을 보고 AI가 간격을 정해준다. 단계별로 구글/애플/카카오에 등록.
 * 퀴즈 보상은 다마고치와 연결: 정답 +20코인, 오답 +5코인.
 */
import { useEffect, useState } from 'react';
import { useParams, useSearchParams, Link } from 'react-router-dom';
import * as api from '../../api/client';
import { useAuth } from '../../context/AuthContext';
import { useGame } from '../../context/GameContext';
import { PageHead, GradeLight } from '../../components/ui';
import { QUIZ_TYPES, MODEL_TIERS, PLAN_TIER } from '../../data/constants';
import { renderCardText, renderQuestion } from '../../components/cardText';
import useIsMobile from '../../hooks/useIsMobile';
import MobileCardDetail from '../mobile/MobileCardDetail';

// 정답 누적 → 등급 계산. 서버도 같은 규칙 (FR-61)
const gradeOf = (n) => (n >= 8 ? 'GREEN_PLUS' : n >= 7 ? 'GREEN' : n >= 3 ? 'YELLOW' : 'RED');

// 문제 유형 코드 → 한글 라벨
const typeKo = (v) => QUIZ_TYPES.find(([code]) => code === v)?.[1] ?? v;

// 모델 코드 → 한글 라벨 (퀴즈에 붙는 배지용)
const modelKo = (v) => MODEL_TIERS.find((m) => m.name === v)?.label ?? v;

// 크레딧 차감량 = 모델 티어. 리뷰 스튜디오와 같은 규칙이라 화면 표기도 맞춰준다
const modelWeight = (v) => MODEL_TIERS.find((m) => m.name === v)?.tier ?? 1;

// 승급 마일스톤 (FR-61). 진행바에 눈금으로 찍고 "다음까지 몇 회"를 계산한다
const MILESTONES = [
  { at: 3, label: '🟡 진행중' },
  { at: 7, label: '🟢 해결' },
  { at: 8, label: '💎 해결+' },
];

// dayOffset(오늘로부터 며칠 뒤) → YYYY-MM-DD. 캘린더 링크에 넘길 날짜를 만든다
const dateAfter = (days) => {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
};

// 화면에 띄울 날짜 — "8월 3일 (월)"처럼 실제로 언제인지 바로 읽히게
const WEEKDAY = ['일', '월', '화', '수', '목', '금', '토'];
const labelDate = (days) => {
  const d = new Date();
  d.setDate(d.getDate() + days);
  const today = days === 0 ? ' · 오늘' : days === 1 ? ' · 내일' : '';
  return `${d.getMonth() + 1}월 ${d.getDate()}일 (${WEEKDAY[d.getDay()]})${today}`;
};

// 학습 계획 기간 — 고른 기간만큼만 AI가 짠다
const PLAN_DAYS = [3, 5, 7, 14];

// 본문·문제 렌더러는 모바일 화면과 공유한다
// 폰이면 데스크톱 본체를 아예 mount하지 않는다.
// 한 컴포넌트 안에서 갈랐더니 조건부 return 위의 useEffect가 그대로 돌아 같은 API를 두 번 때렸다.
// getWeaknessStats처럼 조회할 때마다 통계를 지우고 다시 넣는 API는 두 번 겹치면 한쪽이 빈 목록을 받는다.
export default function CardDetail() {
  return useIsMobile() ? <MobileCardDetail /> : <DesktopCardDetail />;
}

function DesktopCardDetail() {
  const { cardId } = useParams();
  const { user } = useAuth();
  const { spendCredit, recordSubmit, earnCoins, notify } = useGame();

  const [card, setCard] = useState(null);
  const [quiz, setQuiz] = useState(null);
  const [picked, setPicked] = useState(null);
  const [result, setResult] = useState(null); // { isCorrect }
  const [busy, setBusy] = useState(false);
  const [quizType, setQuizType] = useState('MULTIPLE_CHOICE'); // 훅은 early return 위
  const [model, setModel] = useState(MODEL_TIERS[0].name); // 퀴즈 낼 모델
  const [planning, setPlanning] = useState(false); // 학습 계획 생성 중
  const [planDays, setPlanDays] = useState(7); // 고른 학습 기간

  // 지난 문제 보기
  const [pastOpen, setPastOpen] = useState(false);
  const [past, setPast] = useState(null);

  useEffect(() => {
    api.getLearningCard(cardId).then(setCard);
  }, [cardId]);

  // 알림에서 넘어온 단계(?step=3) — 카드를 다 그린 뒤 그 자리로 스크롤하고 잠깐 강조한다
  const [searchParams] = useSearchParams();
  const step = Number(searchParams.get('step')) || 0;
  useEffect(() => {
    if (!card || !step) return;
    // 계획 목록이 DOM에 올라온 다음 프레임에 찾는다
    const id = requestAnimationFrame(() => {
      document.getElementById(`plan-step-${step}`)?.scrollIntoView({ block: 'center', behavior: 'smooth' });
    });
    return () => cancelAnimationFrame(id);
  }, [card, step]);

  if (!card) {
    return (
      <main className="app-main">
        <div className="panel"><p className="note">카드를 펼치는 중…</p></div>
      </main>
    );
  }

  const myTier = PLAN_TIER[user?.planName] ?? 1; // 내 플랜에서 고를 수 있는 모델 상한

  const makeQuiz = async () => {
    if (busy) return;
    if (!spendCredit(modelWeight(model))) return; // 상위 모델일수록 크레딧을 더 먹는다
    setBusy(true);
    setResult(null); setPicked(null);
    try { setQuiz(await api.createQuiz(card.id, quizType, model)); }
    catch (e) { notify(e.message || '문제를 만들지 못했어요'); } // 안 잡으면 콘솔에만 뜨고 화면은 그대로다
    finally { setBusy(false); }
  };

  // 학습 계획 생성 — 고른 기간만큼만 짜준다. 결과는 카드에 저장돼 새로고침해도 남는다
  const makePlan = async () => {
    if (planning) return;
    if (!spendCredit(1)) return;
    setPlanning(true);
    try {
      const updated = await api.createStudyPlan(card.id, planDays);
      setCard((c) => ({ ...c, studyPlan: updated.studyPlan }));
    } catch (e) { notify(e.message || '계획을 짜지 못했어요'); } // 퀴즈와 같은 이유
    finally { setPlanning(false); }
  };

  // 계획 전체를 한 번에 — .ics로 내려받고(외부 캘린더), 서버에도 등록한다(우리 달력·알림).
  // 등록해야 대시보드 달력에 뜨고 그날 아침 알림이 생긴다
  const exportPlan = async () => {
    // 파일은 하나로 — 단계마다 내려받으면 14개가 우수수 떨어진다
    api.downloadPlanIcs(card, plan.map((s, i) => ({
      date: dateAfter(s.dayOffset), stepNo: i + 1, title: s.title, focus: s.focus, minutes: s.minutes,
    })));
    try {
      const updated = await api.registerStudyPlan(card.id);
      setCard((c) => ({ ...c, planStartedAt: updated.planStartedAt }));
      notify(`${plan.length}개 일정을 등록했어요. 대시보드 달력에서 확인하세요`);
    } catch (e) {
      // 파일은 이미 내려갔으므로 그 사실과 실패를 구분해서 알린다
      notify(e.message || '파일은 받았지만 달력 등록에 실패했어요');
    }
  };

  // 유형을 바꿔서 다시 풀기 — 문제를 접고 유형 선택 화면으로
  const resetQuiz = () => { setQuiz(null); setResult(null); setPicked(null); };

  // 제출: streak은 무조건 갱신(정답 무관, FR-74). 정답이면 승급 카운트 + 히스토리
  const submit = async () => {
    if (!picked?.toString().trim() || busy) return;
    setBusy(true);
    try {
      const res = await api.submitQuiz(card.id, quiz.id, picked);
      setResult(res);
      recordSubmit();
      earnCoins(res.isCorrect ? 20 : 5);
      notify(res.isCorrect ? '정답! +20코인 · 연속 학습 유지' : '오답이지만 제출은 인정 +5코인 · 연속 학습 유지');

      if (res.isCorrect) {
        const n = card.correctCount + 1;
        const next = gradeOf(n);
        if (next !== card.grade) { // 등급이 바뀐 순간에만 알린다. 진행바는 correctCount로 알아서 따라온다
          notify(next === 'GREEN_PLUS' ? '💎 재도전 성공 — 해결+ 로 업그레이드!'
            : next === 'GREEN' ? '🟢 약점 해결! 카드가 초록이 됐어요' : '🟡 승급! 학습 진행중');
        }
        setCard((c) => ({ ...c, correctCount: n, grade: next }));
      }
    } finally { setBusy(false); }
  };

  const toggleBm = async () => {
    await api.toggleBookmark(card.id, !card.isBookmarked);
    setCard((c) => ({ ...c, isBookmarked: !c.isBookmarked }));
  };
  const toggleDone = async () => {
    await api.toggleComplete(card.id, !card.isCompleted);
    setCard((c) => ({ ...c, isCompleted: !c.isCompleted }));
  };

  // 지난 문제 열기 — 처음 열 때 한 번만 불러온다
  const togglePast = async () => {
    const open = !pastOpen;
    setPastOpen(open);
    if (open && past === null) setPast(await api.getCardSubmissions(card.id));
  };

  // 다음 마일스톤까지 몇 번 더 맞혀야 하는지. 8회를 넘겼으면 더 오를 곳이 없다
  const nextStep = MILESTONES.find((m) => card.correctCount < m.at);
  const plan = card.studyPlan ?? null; // 서버가 배열로 내려준다. 아직 안 만들었으면 null


  return (
    <main className="app-main">
      {/* 카드로 들어오면 나갈 길이 없었다. 목록으로 돌아가는 링크를 맨 위에 둔다 */}
      <Link to="/app/cards" className="back-link">← 학습카드 목록</Link>

      <PageHead
        badge="LEARNING CARD"
        title={card.category}
        lead={`${card.language} · ${card.level} 수준 맞춤 · 정답 ${card.correctCount}회 누적`}
      />

      {/* 상태 바: 등급 + 북마크/완료 */}
      <div className="filter-bar">
        <GradeLight grade={card.grade} />
        <span style={{ marginLeft: 'auto', display: 'flex', gap: 8 }}>
          <button className="btn wh sm" onClick={toggleBm}>{card.isBookmarked ? '★ 북마크 해제' : '☆ 북마크'}</button>
          <button className="btn wh sm" onClick={toggleDone}>{card.isCompleted ? '↩ 완료 취소' : '✔ 완료 표시'}</button>
        </span>
      </div>

      {/* 승급 진행바 (FR-63) — 언제 어느 등급으로 올라갔는지를 눈금 하나로 보여준다 */}
      <div className="panel grade-progress">
        <div className="row" style={{ justifyContent: 'space-between', marginBottom: 10 }}>
          <b style={{ fontSize: 14 }}>정답 {card.correctCount}회</b>
          <span className="note sm">
            {nextStep ? `${nextStep.label}까지 ${nextStep.at - card.correctCount}회` : '💎 최고 등급 달성'}
          </span>
        </div>
        <div className="gp-track">
          {/* 8회를 100%로 잡고 채운다. 넘어가도 100%에서 멈춘다 */}
          <div className="gp-fill" style={{ width: `${Math.min(100, (card.correctCount / 8) * 100)}%` }} />
          {MILESTONES.map((m) => (
            <span key={m.at}
              className={`gp-mark ${card.correctCount >= m.at ? 'on' : ''}`}
              style={{ left: `${(m.at / 8) * 100}%` }}
              title={`정답 ${m.at}회 · ${m.label}`} />
          ))}
        </div>
        <div className="row" style={{ justifyContent: 'space-between', marginTop: 8 }}>
          {MILESTONES.map((m) => (
            <span key={m.at} className={`note xs ${card.correctCount >= m.at ? 'ok' : ''}`}>{m.label} {m.at}회</span>
          ))}
        </div>
      </div>

      {/* 개념 → 실패/개선 예제 → 핵심 요약 */}
      <div className="panel">
        <h3>📖 개념</h3>
        <p style={{ fontSize: 13.5, lineHeight: 2 }}>{renderCardText(card.conceptContent)}</p>
        {/* 예전에 만든 카드엔 없는 필드라 있을 때만 띄운다 */}
        {card.whyItMatters && (
          <>
            <h3>🔥 그냥 두면 생기는 일</h3>
            <p style={{ fontSize: 13.5, lineHeight: 2 }}>{renderCardText(card.whyItMatters)}</p>
          </>
        )}
      </div>
      <div className="panel-grid c2">
        <div className="panel">
          <h3>기존 코드</h3>
          <pre className="codebox bad">{card.badExample}</pre>
        </div>
        <div className="panel">
          <h3>추천 코드</h3>
          <pre className="codebox good">{card.goodExample}</pre>
        </div>
      </div>
      {card.diffExplain && (
        <div className="panel">
          <h3>🔍 무엇이 바뀌었나</h3>
          <p style={{ fontSize: 13.5, lineHeight: 2 }}>{renderCardText(card.diffExplain)}</p>
        </div>
      )}
      {card.keyPoints && card.keyPoints.length > 0 && (
        <div className="panel">
          <h3>🎯 핵심 3줄</h3>
          <ol className="keypoints">
            {card.keyPoints.map((k, i) => <li key={i}>{renderCardText(k)}</li>)}
          </ol>
        </div>
      )}
      {card.pitfalls && card.pitfalls.length > 0 && (
        <div className="panel">
          <h3>⚠️ 이런 실수를 자주 해요</h3>
          <ol className="keypoints">
            {card.pitfalls.map((p, i) => <li key={i}>{renderCardText(p)}</li>)}
          </ol>
        </div>
      )}
      {card.selfCheck && card.selfCheck.length > 0 && (
        <div className="panel">
          <h3>✅ 다음 리뷰 전 셀프 체크</h3>
          <ol className="keypoints selfcheck">
            {card.selfCheck.map((s, i) => <li key={i}>{renderCardText(s)}</li>)}
          </ol>
        </div>
      )}

      {/* 퀴즈 */}
      <div className="panel">
        <h3>❓ 퀴즈</h3>
        {!quiz ? (
          <>
            <p className="note" style={{ marginBottom: 14 }}>
              유형과 모델을 고르세요. 모델이 높을수록 문제가 깊어지고 크레딧도 더 듭니다.
              제출만 해도 연속 학습은 이어지고, 정답이 쌓이면 등급이 올라갑니다.
            </p>
            <div className="row quiz-tools" style={{ marginBottom: 4 }}>
              <select value={quizType} onChange={(e) => setQuizType(e.target.value)} aria-label="문제 유형">
                {QUIZ_TYPES.map(([v, ko]) => <option key={v} value={v}>{ko}</option>)}
              </select>
              {/* 리뷰 스튜디오와 같은 규칙 — 내 플랜보다 위 티어는 못 고른다 */}
              <select value={model} onChange={(e) => setModel(e.target.value)} aria-label="AI 모델">
                {MODEL_TIERS.map((m) => (
                  <option key={m.name} value={m.name} disabled={m.tier > myTier}>
                    {m.label}
                    {m.tier > myTier ? ` — ${Object.keys(PLAN_TIER).find((k) => PLAN_TIER[k] === m.tier)} 필요` : ''}
                  </option>
                ))}
              </select>
              <button className="btn co sm" onClick={makeQuiz} disabled={busy}>
                {busy ? '문제 만드는 중…' : `문제 생성 (⚡${modelWeight(model)})`}
              </button>
            </div>
          </>
        ) : (
          <>
            <div className="row" style={{ marginBottom: 12 }}>
              <span className="chip navy">{typeKo(quiz.questionType)}</span>
              {/* 어느 모델이 낸 문제인지 남겨야 난이도 차이를 사용자가 납득한다 */}
              {quiz.modelName && <span className="chip low">{modelKo(quiz.modelName)}</span>}
            </div>
            {/* 문제에 코드가 섞여 오면 코드블록으로 떼어 그린다. 줄바꿈과 들여쓰기가 살아야 읽힌다 */}
            <div className="quiz-question">{renderQuestion(quiz.question)}</div>

            {/* 유형별 답안: 선택지가 있으면 버튼, 없으면 직접 입력 */}
            {quiz.options ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                {quiz.options.map((op) => {
                  // 제출 후에는 정답/내 오답을 색으로 표시
                  const state = !result ? '' : op === quiz.answer ? 'on' : op === picked ? 'miss' : '';
                  return (
                    <button key={op}
                      className={`pick ${picked === op && !result ? 'on' : ''} ${state}`}
                      style={{ textAlign: 'left' }}
                      disabled={!!result}
                      onClick={() => setPicked(op)}>
                      {op}
                    </button>
                  );
                })}
              </div>
            ) : (
              <div className="form" style={{ maxWidth: 320 }}>
                <input type="text" value={picked ?? ''} disabled={!!result}
                  placeholder="정답을 입력하세요"
                  onChange={(e) => setPicked(e.target.value)}
                  onKeyDown={(e) => { if (e.key === 'Enter') submit(); }} />
              </div>
            )}

            {!result ? (
              <button className="btn co" style={{ marginTop: 16 }} onClick={submit} disabled={!picked?.toString().trim() || busy}>
                제출
              </button>
            ) : (
              <div style={{ marginTop: 16 }}>
                <p className={result.isCorrect ? 'ok' : 'err'} style={{ fontSize: 14 }}>
                  {result.isCorrect ? '⭕ 정답! 승급 카운트 +1' : `❌ 오답 · 정답은 "${quiz.answer}" — 그래도 제출은 인정!`}
                </p>
                {/* 해설은 정답이어도 항상 보여준다 (복습·이해 강화) */}
                {quiz.explain && (
                  <div className="explain-box">{renderQuestion(quiz.explain)}</div>
                )}
                <div className="row" style={{ marginTop: 14 }}>
                  <button className="btn co sm" onClick={makeQuiz} disabled={busy}>다른 문제 더 풀기 (⚡{modelWeight(model)})</button>
                  <button className="btn wh sm" onClick={resetQuiz}>유형·모델 바꾸기</button>
                </div>
              </div>
            )}
          </>
        )}
      </div>

      {/* 지난 문제 보기 */}
      <div className="panel">
        <div className="row" style={{ justifyContent: 'space-between' }}>
          <h3 style={{ margin: 0 }}>🧾 내가 푼 문제 복습</h3>
          <button className="btn wh sm" onClick={togglePast}>{pastOpen ? '접기 ▲' : '펼치기 ▼'}</button>
        </div>
        <p className="note sm" style={{ marginTop: 8 }}>지금까지 푼 문제와 내 답, 정답, 해설을 다시 봅니다.</p>
        {pastOpen && (
          past === null ? (
            <p className="note" style={{ marginTop: 12 }}>불러오는 중…</p>
          ) : past.length === 0 ? (
            <p className="note" style={{ marginTop: 12 }}>아직 푼 문제가 없어요. 위에서 퀴즈를 한 번 풀어보세요.</p>
          ) : (
            <div style={{ marginTop: 12 }}>
              {past.map((p, i) => (
                <div key={i} className="q-log">
                  <div className="row" style={{ justifyContent: 'space-between', marginBottom: 8 }}>
                    <span className="chip navy">{typeKo(p.questionType)}</span>
                    <span className={`chip ${p.isCorrect ? 'ok-chip' : 'hi'}`}>{p.isCorrect ? '정답' : '오답'}</span>
                  </div>
                  <p className="q">{p.question}</p>
                  <p className="a">내 답: <b>{p.submittedAnswer || '—'}</b> · 정답: <b>{p.answer}</b></p>
                  {p.explain && <p className="a expl">💡 {p.explain}</p>}
                  <p className="note xs mono" style={{ marginTop: 6 }}>{p.submittedAt}</p>
                </div>
              ))}
            </div>
          )
        )}
      </div>

      {/* 학습 계획 — 기간을 고르면 그 기간만큼만 AI가 짠다 */}
      <div className="panel">
        <h3>🗓 학습 계획</h3>
        <p className="note" style={{ marginBottom: 14 }}>
          기간을 고르면 코기가 그 기간에 맞춰 계획을 짜드려요.
        </p>

        <div className="row" style={{ marginBottom: 14 }}>
          {PLAN_DAYS.map((d) => (
            <button key={d}
              className={`btn sm ${planDays === d ? 'co' : 'wh'}`}
              onClick={() => setPlanDays(d)}>
              {d}일
            </button>
          ))}
          <button className="btn co sm" onClick={makePlan} disabled={planning}>
            {planning ? '계획 짜는 중…' : plan ? `${planDays}일로 다시 생성 (⚡1)` : `학습 계획 생성 (⚡1)`}
          </button>
        </div>

        {plan && (
          <>
            {/* 세로 타임라인 — 단계가 시간 순으로 이어지는 게 한눈에 보이도록 */}
            <ol className="plan-timeline">
              {plan.map((s, i) => {
                const date = dateAfter(s.dayOffset); // 캘린더에 넘길 실제 날짜
                return (
                  <li key={i} id={`plan-step-${i + 1}`}
                    className={`plan-step ${step === i + 1 ? 'on' : ''}`}>
                    <span className="ps-dot">
                      <b>{i + 1}</b>
                      <em>{s.minutes}분</em>
                    </span>
                    <div className="ps-body">
                      <div className="ps-head">
                        <b>{s.title}</b>
                        <span className="ps-date">{labelDate(s.dayOffset)}</span>
                      </div>
                      <p className="note sm">{s.focus}</p>
                      {/* 강의·문서·AI 프롬프트 등 그 단계에서 같이 보면 좋은 것 */}
                      {s.resource && <p className="ps-res">📚 {s.resource}</p>}
                      <p className="ps-check">✔ {s.checkpoint}</p>
                      <div className="row" style={{ gap: 6, marginTop: 8, flexWrap: 'wrap' }}>
                        <a className="btn wh sm" href={api.googleCalendarUrl(card, date)} target="_blank" rel="noreferrer">구글</a>
                        <button className="btn wh sm" onClick={() => api.downloadIcs(card, date)}>애플(.ics)</button>
                        <button className="btn wh sm" onClick={async () => { await api.kakaoCalendarAdd(card, date); notify('카카오 캘린더는 비즈니스 인증 후 열려요 (준비 중)'); }}>카카오</button>
                      </div>
                    </div>
                  </li>
                );
              })}
            </ol>

            <div className="row" style={{ marginTop: 14, justifyContent: 'space-between' }}>
              <span className="note sm">
                총 {plan.length}단계 · {plan.reduce((sum, s) => sum + (s.minutes ?? 0), 0)}분
              </span>
              <button className="btn co sm" onClick={exportPlan}>📅 전체 일정 한 번에 등록 &amp; 저장</button>
            </div>
          </>
        )}
      </div>

      <p className="note sm mt18">
        이 주제를 더 깊게? <Link to="/app/courses" className="link-line">추천 강의</Link> ·{' '}
        <Link to="/app/skills" className="link-line">AI 스킬 추천</Link>
      </p>
    </main>
  );
}
