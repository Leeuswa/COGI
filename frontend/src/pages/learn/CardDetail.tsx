/*
 * 학습카드 상세 (LRN-002, API-044~047 + 캘린더 연동)
 * 카드 한 장에 이 서비스 학습 루프가 다 들어있다:
 *   개념 → 예제 → 퀴즈 생성(크레딧) → 제출(streak, 정답 무관) → 정답이면 승급 카운트
 *   RED → YELLOW(정답 3) → GREEN(정답 7) = 약점 해결 (FR-61)
 * + 북마크/완료 토글, 승급 히스토리(FR-63),
 * + 캘린더 등록(요구사항 8): 구글=URL 실동작 / 애플=.ics 다운로드 / 카카오=대기.
 * 퀴즈 보상은 다마고치와 연결: 정답 +20코인, 오답 +5코인 (원본 수치 그대로).
 */
import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import * as api from '../../api/client';
import { useGame } from '../../context/GameContext';
import { PageHead, GradeLight } from '../../components/ui';
import { QUIZ_TYPES } from '../../data/constants';

// 정답 누적 → 등급 계산. 서버도 같은 규칙 (FR-61)
// 승급 규칙 (FR-61~62): 정답만 카운트 → 3=노랑, 7=초록(해결).
// 초록 이후 재도전에서 또 맞히면 8부터 '해결+'(GREEN_PLUS). 오답은 카운트 미증가 = 강등 없음.
const gradeOf = (n) => (n >= 8 ? 'GREEN_PLUS' : n >= 7 ? 'GREEN' : n >= 3 ? 'YELLOW' : 'RED');

export default function CardDetail() {
  const { cardId } = useParams();
  const { spendCredit, recordSubmit, earnCoins, notify } = useGame();

  const [card, setCard] = useState(null);
  const [history, setHistory] = useState([]);
  const [quiz, setQuiz] = useState(null);
  const [picked, setPicked] = useState(null);
  const [result, setResult] = useState(null);  // { isCorrect }
  const [busy, setBusy] = useState(false);
  const [calDate, setCalDate] = useState(() => {
    const d = new Date(); d.setDate(d.getDate() + 1); // 기본값: 내일
    return d.toISOString().slice(0, 10);
  });
  const [quizType, setQuizType] = useState('MULTIPLE_CHOICE'); // 문제 유형 (FR-64) — 훅은 반드시 early return 위

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

  // 제출: streak은 여기서 무조건 갱신(정답 무관, FR-74). 정답이면 승급 카운트+히스토리
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

      {/* 개념 → 실패/개선 예제 → 핵심 요약 순서로 읽고 퀴즈로 */}
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
      {card.keyPoints && (
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
              {/* 문제 유형 선택 (FR-64) */}
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
              <span className="chip navy">{QUIZ_TYPES.find(([v]) => v === quiz.questionType)?.[1]}</span>
            </div>
            <p style={{ fontSize: 14, marginBottom: 14, lineHeight: 1.9 }}>{quiz.question}</p>

            {/* 유형별 답안 입력: 선택지가 있으면 버튼(객관식/OX), 없으면 직접 입력(주관식/빈칸) */}
            {quiz.options ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                {quiz.options.map((op) => (
                  <button key={op}
                    className={`pick ${picked === op ? 'on' : ''}`}
                    style={{ textAlign: 'left' }}
                    disabled={!!result}
                    onClick={() => setPicked(op)}>
                    {op}
                  </button>
                ))}
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
                  {result.isCorrect ? '⭕ 정답! 승급 카운트 +1' : `❌ 오답. 정답은 "${quiz.answer}" — 그래도 제출은 인정!`}
                </p>
                {!result.isCorrect && quiz.explain && (
                  <p className="note" style={{ marginTop: 8, lineHeight: 1.9 }}>💡 {quiz.explain}</p>
                )}
                <button className="btn wh sm" style={{ marginTop: 12 }} onClick={makeQuiz}>
                  다른 문제 더 풀기 (⚡1)
                </button>
              </div>
            )}
          </>
        )}
      </div>

      {/* 캘린더 등록 (요구사항 8: 학습 가이드를 일정으로) */}
      <div className="panel">
        <h3>🗓 학습 미션을 캘린더에</h3>
        <p className="note" style={{ marginBottom: 14 }}>
          "이따 해야지"는 안 하게 돼요. 15분짜리 미션으로 박아두세요.
        </p>
        <div className="filter-bar" style={{ marginBottom: 0 }}>
          <input type="date" value={calDate} onChange={(e) => setCalDate(e.target.value)} />
          <a className="btn wh sm" href={api.googleCalendarUrl(card, calDate)} target="_blank" rel="noreferrer">
            구글 캘린더
          </a>
          <button className="btn wh sm" onClick={() => api.downloadIcs(card, calDate)}>
            애플 캘린더 (.ics)
          </button>
          <button className="btn wh sm" onClick={async () => { await api.kakaoCalendarAdd(card, calDate); notify('카카오 캘린더는 비즈니스 인증 후 열려요 (준비 중)'); }}>
            카카오 캘린더
          </button>
        </div>
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
