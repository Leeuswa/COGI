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
import { useAuth } from "../../context/AuthContext";
import { useGame } from "../../context/GameContext";
import CorgiDevice from "../../components/CorgiDevice";
import { catKo } from "../../data/constants";
import "../../styles/mobile/dashboard.css";

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

export default function MobileDashboard() {
  const { user } = useAuth();
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

  // 이번 주 7칸만. 폰에서 한 달 달력은 칸이 너무 작아 못 누른다
  const today = new Date();
  const weekStart = new Date(today);
  weekStart.setDate(today.getDate() - today.getDay());
  const week = Array.from({ length: 7 }, (_, i) => {
    const d = new Date(weekStart);
    d.setDate(weekStart.getDate() + i);
    const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
    return { key, date: d.getDate(), wd: WEEKDAYS[i], hit: S.submitDays.includes(key), isToday: d.toDateString() === today.toDateString() };
  });

  const left = creditLimit - S.creditUsed;
  const pct = Math.min(100, Math.round((S.creditUsed / creditLimit) * 100));

  // 오늘 뭘 해야 하는지 하나만 고른다. 앱은 선택지를 늘어놓지 않는다
  const todo = !S.submitDays.includes(week[today.getDay()].key)
    ? { icon: "📗", title: "오늘 퀴즈 아직이에요", desc: "한 문제면 연속 학습 유지", to: "/app/cards", cta: "학습카드 풀기" }
    : weakness.length > 0
      ? { icon: "🎯", title: `${catKo(weakness[0].category)} 약점이 남아 있어요`, desc: "학습카드로 바로 잡아요", to: "/app/weakness", cta: "약점 보기" }
      : { icon: "🔍", title: "코드 리뷰 받아볼까요", desc: "붙여넣으면 코기가 읽어요", to: "/app/paste", cta: "리뷰 받기" };

  return (
    <main className="mapp mdash">
      {/* 1) 이번 주 학습 */}
      <section className="mcard">
        <div className="mcard-head">
          <h2>이번 주 학습</h2>
          {/* 전체 제출일이 아니라 이번 주 것만 센다. 안 그러면 불꽃 없이 숫자만 뜬다 */}
          <span className="mnote">{week.filter((d) => d.hit).length}일 완료</span>
        </div>
        <div className="mweek">
          {week.map((d) => (
            <span key={d.key} className={["mday", d.hit && "hit", d.isToday && "today"].filter(Boolean).join(" ")}>
              <i>{d.wd}</i>
              <b>{d.hit ? "🔥" : d.date}</b>
            </span>
          ))}
        </div>
      </section>

      {/* 2) 오늘 요약 — 4개를 한 줄로. 2×2로 두면 코기가 화면 밖으로 밀린다 */}
      <div className="mchips">
        <span className="mchip"><em>🔥</em><b>{S.streak}</b><i>일 연속</i></span>
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
    </main>
  );
}
