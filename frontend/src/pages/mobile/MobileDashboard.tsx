/*
 * 모바일 전용 대시보드.
 * 데스크톱 Dashboard를 CSS로 접는 게 아니라 화면 자체를 따로 짰다.
 * 폰에서는 정보 밀도와 손가락 동선이 달라서, 같은 데이터라도 묶는 방식이 달라야 한다.
 *
 *   1) 학습 달력 — 이번 주 7칸만. 한 달 전체는 폰에서 너무 잘다
 *   2) 오늘 요약 — 숫자 4개를 한 줄로
 *   3) 코기 — 리텐션 장치
 *   4) 오늘 할 일 — 지금 누를 것 하나만. 엄지가 닿는 아래쪽에 둔다
 *
 * 스크롤 없이 한 화면에 들어가야 해서 약점 목록과 주간 성적 차트는 뺐다.
 * 둘 다 하단 탭(성장)으로 갈 수 있고, 약점은 4)의 할 일 문구가 대신 알려준다.
 */
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import * as api from "../../api/client";
import { useGame } from "../../context/GameContext";
import CorgiDevice from "../../components/CorgiDevice";
import { catKo } from "../../data/constants";
import "../../styles/mobile/dashboard.css";

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

// 날짜 계산 — toISOString은 UTC로 밀려서 자정 근처에 하루가 어긋난다. 로컬 기준으로 직접 만든다
const addDays = (base, n) => {
  const d = new Date(base);
  d.setDate(d.getDate() + n);
  return d;
};
const ymdKey = (d) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;

// 넘길 수 있는 주 범위. 받아 오는 계획 구간과 같은 값이라 빈 주로 넘어갈 일이 없다
const MAX_WEEK = 12;

export default function MobileDashboard() {
  const { S, creditLimit, syncServer, checkIn } = useGame();

  const [weakness, setWeakness] = useState([]);

  useEffect(() => {
    Promise.all([api.getCreditUsage(), api.getRetentionStatus()])
      .then(([c, r]) => syncServer(c, r))
      .catch(() => {});
    checkIn();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    api.getWeaknessStats().then(setWeakness).catch(() => {});
  }, []);

  // 등록해 둔 학습 계획 — 주간 스트립에 표시하고, 오늘 몫은 알림으로 만든다(데스크톱과 같은 규칙).
  // 알림 생성은 쓰기라 조회와 분리해 POST한다. 같은 단계는 서버가 하루 한 번만 만든다
  const [planDates, setPlanDates] = useState([]);
  const [dayPick, setDayPick] = useState(null); // 주간 스트립에서 누른 날 — { key, plans }
  // 화살표로 주를 넘길 수 있어서 한 달치만 받으면 이전 주가 빈다.
  // 넘길 수 있는 범위(MAX_WEEK)와 정확히 같은 구간을 한 번에 받아 둔다 — 넘길 때마다 다시 안 부른다
  useEffect(() => {
    const t = new Date();
    const first = addDays(t, -t.getDay() - MAX_WEEK * 7);              // 가장 이른 주의 일요일
    const last = addDays(t, -t.getDay() + (MAX_WEEK + 1) * 7 - 1);     // 가장 늦은 주의 토요일
    api.getPlanDates(ymdKey(first), ymdKey(last)).then(setPlanDates).catch(() => setPlanDates([]));
    api.ensureTodayPlanNotifications().catch(() => {});
  }, []);

  // 7칸만. 폰에서 한 달 달력은 칸이 너무 작아 못 누른다 — 대신 주 단위로 넘긴다
  const [weekOffset, setWeekOffset] = useState(0); // 0 = 이번 주, -1 = 지난 주
  const today = new Date();
  const weekStart = addDays(today, -today.getDay() + weekOffset * 7);
  const week = Array.from({ length: 7 }, (_, i) => {
    const d = addDays(weekStart, i);
    const key = ymdKey(d);
    return { key, date: d.getDate(), wd: WEEKDAYS[i], hit: S.submitDays.includes(key), isToday: d.toDateString() === today.toDateString() };
  });
  const weekLabel = weekOffset === 0 ? "이번 주"
    : weekOffset === -1 ? "지난 주"
    : weekOffset === 1 ? "다음 주"
    : `${Math.abs(weekOffset)}주 ${weekOffset < 0 ? "전" : "후"}`;

  const left = creditLimit - S.creditUsed;
  const pct = Math.min(100, Math.round((S.creditUsed / creditLimit) * 100));

  // 오늘 뭘 해야 하는지 하나만 고른다. 앱은 선택지를 늘어놓지 않는다
  // 주를 넘겨 봐도 "오늘 할 일"은 오늘 기준이다 — week에서 꺼내면 지난 주를 보는 동안 문구가 바뀐다
  const todo = !S.submitDays.includes(ymdKey(today))
    ? { icon: "📗", title: "오늘 퀴즈 아직이에요", desc: "한 문제면 연속 학습 유지", to: "/app/cards", cta: "학습카드 풀기" }
    : weakness.length > 0
      ? { icon: "🎯", title: `${catKo(weakness[0].category)} 약점이 남아 있어요`, desc: "학습카드로 바로 잡아요", to: "/app/weakness", cta: "약점 보기" }
      : { icon: "🔍", title: "코드 리뷰 받아볼까요", desc: "붙여넣으면 코기가 읽어요", to: "/app/paste", cta: "리뷰 받기" };

  return (
    <main className="mapp mdash">
      {/* 1) 주간 학습 — 화살표로 지난 주 계획까지 거슬러 본다 */}
      <section className="mcard">
        <div className="mcard-head mweek-head">
          <button type="button" className="mweek-nav" aria-label="이전 주"
            disabled={weekOffset <= -MAX_WEEK}
            onClick={() => setWeekOffset((w) => Math.max(-MAX_WEEK, w - 1))}>‹</button>
          <h2>{weekLabel} 학습</h2>
          <button type="button" className="mweek-nav" aria-label="다음 주"
            disabled={weekOffset >= MAX_WEEK}
            onClick={() => setWeekOffset((w) => Math.min(MAX_WEEK, w + 1))}>›</button>
          {/* 전체 제출일이 아니라 보고 있는 주 것만 센다. 안 그러면 불꽃 없이 숫자만 뜬다 */}
          <span className="mnote">{week.filter((d) => d.hit).length}일 완료</span>
        </div>
        <div className="mweek">
          {week.map((d) => (
            <DayCell key={d.key} d={d}
              plans={planDates.filter((p) => p.date === d.key)}
              onPick={setDayPick} />
          ))}
        </div>
      </section>

      {/* 2) 오늘 요약 — 4개를 한 줄로. 2×2로 두면 코기가 화면 밖으로 밀린다 */}
      <div className="mchips">
        {/* 끊겨도 줄지 않는 누적 학습일. S.streak(연속)과 다른 값이다 — 데스크톱 상단과 맞춘다 */}
        <span className="mchip"><em>🔥</em><b>{S.totalDays}</b><i>학습일</i></span>
        <span className={`mchip ${pct >= 90 ? "warn" : ""}`}><em>⚡</em><b>{left}</b><i>크레딧</i></span>
        <span className="mchip"><em>🎯</em><b>{weakness.length}</b><i>약점</i></span>
        <span className="mchip"><em>🪙</em><b>{S.coins}</b><i>코인</i></span>
      </div>

      {/* 3) 코기 */}
      <CorgiDevice />

      {/* 4) 오늘 할 일 — 코기를 보고 나서 바로 누르도록 맨 아래(엄지 자리)에 둔다 */}
      <section className="mcard mtodo">
        <span className="mt-emoji">{todo.icon}</span>
        <div className="mt-text">
          <b>{todo.title}</b>
          <p>{todo.desc}</p>
        </div>
        <Link className="btn co sm" to={todo.to}>{todo.cta}</Link>
      </section>

      {/* 날짜를 누르면 뜨는 시트 — 지난 날짜도 그대로 열린다(알림은 사라져도 계획은 남는다) */}
      {dayPick && (
        <>
          <div className="mplan-dim" onClick={() => setDayPick(null)} />
          <div className="mplan-sheet" role="dialog" aria-label={`${dayPick.key} 학습 계획`}>
            <div className="ms-grab" />
            <h2>📘 {dayPick.key}</h2>
            <ol className="mplan-list">
              {dayPick.plans.map((p) => (
                <li key={`${p.cardId}-${p.stepNo}`}>
                  <div className="mpl-head">
                    <span className="mpl-cat">{catKo(p.category)}</span>
                    <b>{p.stepNo}단계 · {p.title}</b>
                    {p.minutes != null && <em>{p.minutes}분</em>}
                  </div>
                  {p.focus && <p>{p.focus}</p>}
                  <Link className="btn wh sm full" to={`/app/cards/${p.cardId}?step=${p.stepNo}`}
                    onClick={() => setDayPick(null)}>
                    학습카드에서 보기
                  </Link>
                </li>
              ))}
            </ol>
            <button className="btn co sm full" onClick={() => setDayPick(null)}>닫기</button>
          </div>
        </>
      )}
    </main>
  );
}

// 주간 스트립 한 칸. 계획이 있는 날만 눌러서 그날 계획을 펼친다
function DayCell({ d, plans, onPick }) {
  const cls = ["mday", d.hit && "hit", plans.length > 0 && "plan", d.isToday && "today"]
    .filter(Boolean).join(" ");
  const inner = <><i>{d.wd}</i><b>{d.hit ? "🔥" : d.date}</b></>;

  if (plans.length === 0) return <span className={cls}>{inner}</span>;
  return (
    <button type="button" className={cls} onClick={() => onPick({ key: d.key, plans })}>{inner}</button>
  );
}
