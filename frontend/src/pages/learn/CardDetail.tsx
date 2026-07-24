/*
 * 학습카드 상세 (LRN-002, API-044~047 + 지난 문제 보기 + 캘린더 학습 일정)
 * 카드 한 장에 학습 루프가 다 들어있다:
 *   개념 → 예제 → 퀴즈 생성(크레딧) → 제출(streak, 정답 무관) → 정답이면 승급 카운트
 *   RED → YELLOW(정답 3) → GREEN(정답 7) → GREEN_PLUS(재도전 8) (FR-61)
 * + 북마크/완료 토글, 승급 히스토리(FR-63), 지난 문제 복습(내 답·정답·해설)
 * + 캘린더 학습 일정: 오늘 기준 간격(3일/1주/2주/한달)으로 복습 일정을 뽑아 구글/애플/카카오에 등록.
 * 퀴즈 보상은 다마고치와 연결: 정답 +20코인, 오답 +5코인.
 */
import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import * as api from '../../api/client';
import { useGame } from '../../context/GameContext';
import { PageHead, GradeLight } from '../../components/ui';
import { QUIZ_TYPES } from '../../data/constants';

// 정답 누적 → 등급 계산. 서버도 같은 규칙 (FR-61)
const gradeOf = (n) => (n >= 8 ? 'GREEN_PLUS' : n >= 7 ? 'GREEN' : n >= 3 ? 'YELLOW' : 'RED');

// 문제 유형 코드 → 한글 라벨
const typeKo = (v) => QUIZ_TYPES.find(([code]) => code === v)?.[1] ?? v;

// 복습 일정 프리셋: 오늘 기준 얼마나 멀리까지 복습을 걸어둘지
const HORIZONS: [string, number][] = [['3일', 3], ['1주', 7], ['2주', 14], ['한달', 30]];
// 선택한 기간 안에서 간격 복습(1·3·7·14·30일 뒤) 체크포인트를 만든다
const buildSchedule = (days) => {
  const today = new Date();
  return [1, 3, 7, 14, 30]
    .filter((n) => n <= days)
    .map((n) => {
      const d = new Date(today);
      d.setDate(d.getDate() + n);
      return { n, date: d.toISOString().slice(0, 10) };
    });
};

export default function CardDetail() {
  const { cardId } = useParams();
  const { spendCredit, recordSubmit, earnCoins, notify } = useGame();

  const [card, setCard] = useState(null);
  const [history, setHistory] = useState([]);
  const [quiz, setQuiz] = useState(null);
  const [picked, setPicked] = useState(null);
  const [result, setResult] = useState(null); // { isCorrect }
  const [busy, setBusy] = useState(false);
  const [quizType, setQuizType] = useState('MULTIPLE_CHOICE'); // 훅은 early return 위

  // 지난 문제 보기
  const [pastOpen, setPastOpen] = useState(false);
  const [past, setPast] = useState(null);

  // 캘린더 학습 일정
  const [horizon, setHorizon] = useState(7);
  const [schedule, setSchedule] = useState(null);

  useEffect(() => {
    api.getLearningCard(cardId).then(setCard);
    api.getCardHistory(cardId).then(setHistory);
  }, [cardId]);

  if (!card) {
    return (
      <main className="app-main">
        <div className="panel"><p className="note">카드를 펼치는 중…</p></div>
      </main>
    );
  }

  const makeQuiz = async () => {
    if (busy) return;
    if (!spendCredit(1)) return;
    setBusy(true);
    setResult(null); setPicked(null);
    try { setQuiz(await api.createQuiz(card.id, quizType)); }
    finally { setBusy(false); }
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
        if (next !== card.grade) {
          setHistory((h) => [...h, { date: new Date().toISOString().slice(0, 10), grade: next, note: `정답 ${n}회 달성 → ${next} 승급` }]);
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

  const genSchedule = () => setSchedule(buildSchedule(horizon));

  return (
    <main className="app-main">
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

      {/* 개념 → 실패/개선 예제 → 핵심 요약 */}
      <div className="panel">
        <h3>📖 개념</h3>
        <p style={{ fontSize: 13.5, lineHeight: 2 }}>{card.conceptContent}</p>
      </div>
      <div className="panel-grid c2">
        <div className="panel">
          <h3>❌ 이렇게 하면</h3>
          <pre className="codebox bad">{card.badExample}</pre>
        </div>
        <div className="panel">
          <h3>✅ 이렇게 고치면</h3>
          <pre className="codebox good">{card.goodExample}</pre>
        </div>
      </div>
      {card.keyPoints && card.keyPoints.length > 0 && (
        <div className="panel">
          <h3>🎯 핵심 3줄</h3>
          <ol className="keypoints">
            {card.keyPoints.map((k, i) => <li key={i}>{k}</li>)}
          </ol>
        </div>
      )}

      {/* 퀴즈 */}
      <div className="panel">
        <h3>❓ 퀴즈</h3>
        {!quiz ? (
          <>
            <p className="note" style={{ marginBottom: 14 }}>
              문제 유형을 고르고 생성하세요. 제출만 해도 연속 학습이 이어지고, 정답이 쌓이면 등급이 올라요.
            </p>
            <div className="row" style={{ marginBottom: 4 }}>
              <select value={quizType} onChange={(e) => setQuizType(e.target.value)} aria-label="문제 유형">
                {QUIZ_TYPES.map(([v, ko]) => <option key={v} value={v}>{ko}</option>)}
              </select>
              <button className="btn co sm" onClick={makeQuiz} disabled={busy}>
                {busy ? '문제 만드는 중…' : '문제 생성 (⚡1)'}
              </button>
            </div>
          </>
        ) : (
          <>
            <div className="row" style={{ marginBottom: 12 }}>
              <span className="chip navy">{typeKo(quiz.questionType)}</span>
            </div>
            <p style={{ fontSize: 14, marginBottom: 14, lineHeight: 1.9 }}>{quiz.question}</p>

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
                  <div className="explain-box">💡 {quiz.explain}</div>
                )}
                <div className="row" style={{ marginTop: 14 }}>
                  <button className="btn co sm" onClick={makeQuiz} disabled={busy}>다른 문제 더 풀기 (⚡1)</button>
                  <button className="btn wh sm" onClick={resetQuiz}>다른 유형으로 변경</button>
                </div>
              </div>
            )}
          </>
        )}
      </div>

      {/* 지난 문제 보기 */}
      <div className="panel">
        <div className="row" style={{ justifyContent: 'space-between' }}>
          <h3 style={{ margin: 0 }}>🧾 지난 문제 보기</h3>
          <button className="btn wh sm" onClick={togglePast}>{pastOpen ? '접기 ▲' : '펼치기 ▼'}</button>
        </div>
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

      {/* 캘린더 학습 일정 */}
      <div className="panel">
        <h3>🗓 학습 미션을 캘린더에</h3>
        <p className="note" style={{ marginBottom: 14 }}>
          "이따 해야지"는 안 하게 돼요. 오늘 기준으로 복습 일정을 뽑아 15분짜리 미션으로 박아두세요.
        </p>
        <div className="row">
          {HORIZONS.map(([ko, d]) => (
            <button key={d}
              className={`btn sm ${horizon === d ? 'co' : 'wh'}`}
              onClick={() => { setHorizon(d); setSchedule(null); }}>
              {ko}
            </button>
          ))}
          <button className="btn co sm" onClick={genSchedule}>학습 일정 생성하기</button>
        </div>

        {schedule && (
          <div style={{ marginTop: 14 }}>
            {schedule.length === 0 ? (
              <p className="note">일정이 없어요.</p>
            ) : schedule.map(({ n, date }) => (
              <div key={date} className="sched-row">
                <span className="d">D+{n}</span>
                <span className="mono note" style={{ minWidth: 92 }}>{date}</span>
                <span style={{ marginLeft: 'auto', display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                  <a className="btn wh sm" href={api.googleCalendarUrl(card, date)} target="_blank" rel="noreferrer">구글</a>
                  <button className="btn wh sm" onClick={() => api.downloadIcs(card, date)}>애플(.ics)</button>
                  <button className="btn wh sm" onClick={async () => { await api.kakaoCalendarAdd(card, date); notify('카카오 캘린더는 비즈니스 인증 후 열려요 (준비 중)'); }}>카카오</button>
                </span>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* 승급 히스토리 (FR-63) */}
      <div className="panel">
        <h3>📜 히스토리</h3>
        {history.map((h, i) => (
          <div key={i} className="meta-row" style={{ marginTop: i ? 8 : 0 }}>
            <span style={{ fontFamily: 'inherit', fontSize: 13 }}>{h.note}</span>
            <span className="mono note xs">{h.date} · {h.grade}</span>
          </div>
        ))}
      </div>

      <p className="note sm mt18">
        이 주제를 더 깊게? <Link to="/app/courses" className="link-line">추천 강의</Link> ·{' '}
        <Link to="/app/skills" className="link-line">AI 스킬 추천</Link>
      </p>
    </main>
  );
}
