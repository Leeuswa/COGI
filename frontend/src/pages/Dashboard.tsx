/*
 * 대시보드 — 로그인 후 홈.
 * 100% 배율에서 한 화면에 들어오는 게 전제라 2열 고정으로 짰다.
 *   왼쪽  코기 다마고치 + 지금 나의 약점 (LRN-001)
 *   오른쪽 학습 달력(RET-001) + 주간 이슈 추이
 * 로그인 후 홈은 대시보드와 푸터로 끝난다 — 랜딩의 서비스 안내는 붙이지 않는다.
 */
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import * as api from "../api/client";
import { useAuth } from "../context/AuthContext";
import { useGame } from "../context/GameContext";
import CorgiDevice from "../components/CorgiDevice";
import TrendChart from "../components/TrendChart";
import { PageHead } from "../components/ui";
import { catKo } from "../data/constants";
import useIsMobile from "../hooks/useIsMobile";
import MobileDashboard from "./mobile/MobileDashboard";

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

// 폰이면 데스크톱 본체를 아예 mount하지 않는다.
// 한 컴포넌트 안에서 갈랐더니 조건부 return 위의 useEffect가 그대로 돌아 같은 API를 두 번 때렸다.
// getWeaknessStats처럼 조회할 때마다 통계를 지우고 다시 넣는 API는 두 번 겹치면 한쪽이 빈 목록을 받는다.
export default function Dashboard() {
  return useIsMobile() ? <MobileDashboard /> : <DesktopDashboard />;
}

function DesktopDashboard() {
  const { user } = useAuth();
  const { S, creditLimit, syncServer, checkIn } = useGame();

  const [weakness, setWeakness] = useState([]);
  const [trend, setTrend] = useState(null);

  // 서버가 집계한 크레딧(API-055)·스트릭(API-051)으로 로컬 게임 상태를 보정
  useEffect(() => {
    Promise.all([api.getCreditUsage(), api.getRetentionStatus()])
      .then(([c, r]) => syncServer(c, r))
      .catch(() => {});
    checkIn(); // 매일 첫 방문에 출석 코인 +10 (하루 1회)
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    api.getWeaknessStats().then(setWeakness).catch(() => {});
  }, []);

  // 이슈 추이는 성장추이 화면이 쓰는 API를 그대로 재사용한다
  useEffect(() => {
    api.getMyLinkedRepos()
      .then((repos) => {
        if (!repos?.length) return setTrend([]);
        return api.getGrowthTrend(repos[0].repoId, { period: "4w", memberId: user.userId }).then(setTrend);
      })
      .catch(() => setTrend([]));
  }, [user.userId]);

  // 이번 달 달력. 1일 앞 빈칸을 채워 요일을 맞춘다
  const now = new Date();
  const year = now.getFullYear();
  const month = now.getMonth();
  const pad = new Date(year, month, 1).getDay();
  const lastDate = new Date(year, month + 1, 0).getDate();
  const ymd = (d: number) =>
    `${year}-${String(month + 1).padStart(2, "0")}-${String(d).padStart(2, "0")}`;
  const todayKey = ymd(now.getDate());

  const pct = Math.min(100, Math.round((S.creditUsed / creditLimit) * 100));
  const dropped = trend?.length > 1 ? trend[0].issueCount - trend[trend.length - 1].issueCount : 0;


  return (
    <>
      <main className="app-main dash">
        <div className="dash-top">
          <PageHead
            badge="DASHBOARD"
            badgeCls="co"
            title={`안녕하세요, ${user.name || user.email?.split("@")[0] || "코기"}님`}
            lead={"오늘도 코기 밥 주고 가세요.\n출석과 학습카드의 문제풀이로 코인을 얻을 수 있습니다."}
          />
          {/* 크레딧 게이지는 뺐다 — 바로 아래 [남은 크레딧] 카드와 상단 네비가 같은 값을 이미 보여준다 */}
        </div>

        {/* 오늘 상태 한 줄 요약 — 숫자만 훑고 지나갈 수 있게 */}
        <div className="hud-row">
          <div className="hud-card">
            <span className="sc-label">연속 학습</span>
            <b className="sc-value">{S.streak}<em>일</em></b>
          </div>
          <div className={`hud-card ${pct >= 90 ? "warn" : ""}`}>
            <span className="sc-label">남은 크레딧</span>
            <b className="sc-value">{creditLimit - S.creditUsed}<em>개</em></b>
          </div>
          <div className="hud-card">
            <span className="sc-label">지금 약점</span>
            <b className="sc-value">{weakness.length}<em>개</em></b>
          </div>
          <div className="hud-card">
            <span className="sc-label">이번 달 학습</span>
            <b className="sc-value">{S.submitDays.length}<em>일</em></b>
          </div>
        </div>

        <div className="dash-grid">
          {/* ── 왼쪽: 코기와 약점 ── */}
          <div className="dash-col">
            <CorgiDevice />

            <div className="panel weak-panel">
              <div className="dash-head">
                <h3>🎯 지금 나의 약점</h3>
                <Link className="link-line" to="/app/weakness">전체 보기 →</Link>
              </div>

              {weakness.length === 0 ? (
                <p className="note">아직 데이터가 없어요.<br />리뷰를 받으면 반복 실수가 여기 쌓입니다.</p>
              ) : (
                <div className="weak-list">
                  {weakness.map((w) => (
                    <div key={w.id} className="weak-tile">
                      <span className="wt-count">{w.occurrenceCount}회</span>
                      <span className="wt-body">
                        <b>{catKo(w.category)}</b>
                        <em>{w.language}</em>
                      </span>
                    </div>
                  ))}
                </div>
              )}

              <div className="dash-foot">
                <Link className="btn co sm" to="/app/cards">학습카드 풀기</Link>
                <Link className="btn wh sm" to="/app/paste">코드 리뷰 받기</Link>
              </div>
            </div>
          </div>

          {/* ── 오른쪽: 학습 달력과 이슈 추이 ── */}
          <div className="dash-col">
            <div className="panel cal-panel">
              <div className="dash-head">
                <h3>🔥 연속 학습 {S.streak}일</h3>
                <span className="note sm">{year}년 {month + 1}월</span>
              </div>

              <div className="cal-grid">
                {WEEKDAYS.map((w) => <span key={w} className="cal-wd">{w}</span>)}
                {Array.from({ length: pad }, (_, i) => <span key={`p${i}`} className="cal-pad" />)}
                {Array.from({ length: lastDate }, (_, i) => {
                  const key = ymd(i + 1);
                  const hit = S.submitDays.includes(key);
                  return (
                    <span key={key}
                      className={["cal-day", hit && "hit", key === todayKey && "today"].filter(Boolean).join(" ")}
                      title={`${key}${hit ? " — 퀴즈 제출함" : ""}`}>
                      {hit ? "🔥" : i + 1}
                    </span>
                  );
                })}
              </div>

            </div>

            <div className="panel trend-panel">
              <div className="dash-head">
                <h3>📉 주간 이슈 빈도</h3>
                {dropped > 0 && <span className="trend-badge">▼ {dropped}건 줄었어요</span>}
              </div>
              {!trend ? (
                <p className="note">집계 중…</p>
              ) : trend.length === 0 ? (
                <p className="note">아직 집계할 리뷰가 없어요.<br />PR이 쌓이면 주 단위로 그려집니다.</p>
              ) : (
                <>
                  <TrendChart data={trend} height={230} />
                  <div className="chart-legend">
                    <span><i style={{ background: "var(--coral)" }} />발생</span>
                    <span><i style={{ background: "var(--mint)" }} />해결</span>
                  </div>
                </>
              )}
            </div>
          </div>
        </div>
      </main>
    </>
  );
}
