/*
 * 모바일 전용 하단 탭바 — 네이티브 앱처럼 엄지로 닿는 자리에 주요 이동을 둔다.
 * 상단 GNB의 가로 링크는 모바일에서 숨기고(responsive.css) 이걸로 대체한다.
 * 로그인 상태에서만 뜬다. 탭에 없는 화면은 '더보기'에서 연다.
 */
import { useState } from "react";
import { NavLink, Link, useLocation } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

// 엄지로 자주 누르는 5개만 탭에 둔다. 나머지는 더보기 시트로
const TABS = [
  { to: "/app", icon: "🏠", label: "홈" },
  { to: "/app/prs", icon: "🔍", label: "리뷰" },
  { to: "/app/cards", icon: "📗", label: "학습" },
  { to: "/app/growth", icon: "📈", label: "성장" },
];

// 더보기 시트에 담을 나머지 화면
const MORE = [
  { to: "/app/weakness", icon: "🎯", label: "약점 통계" },
  { to: "/app/paste", icon: "🛠", label: "리뷰 스튜디오" },
  { to: "/app/history", icon: "🧾", label: "리뷰 히스토리" },
  { to: "/app/courses", icon: "🎓", label: "강의 추천" },
  { to: "/app/skills", icon: "🤖", label: "AI 스킬 추천" },
  { to: "/app/reports", icon: "📮", label: "주간 리포트" },
  { to: "/app/team", icon: "👥", label: "팀" },
  { to: "/app/plan", icon: "💳", label: "요금제" },
  { to: "/app/faq", icon: "❓", label: "FAQ" },
];

export default function MobileTabBar() {
  const { user } = useAuth();
  const [openMore, setOpenMore] = useState(false);
  const loc = useLocation();

  if (!user) return null; // 로그인 전에는 안 띄운다

  return (
    <>
      {/* 더보기 시트 — 탭에 없는 화면들 */}
      {openMore && (
        <>
          <div className="mtab-dim" onClick={() => setOpenMore(false)} />
          <div className="mtab-sheet" role="dialog" aria-label="더보기">
            <div className="ms-grab" />
            <div className="ms-grid">
              {MORE.map((m) => (
                <Link key={m.to} to={m.to} className="ms-item" onClick={() => setOpenMore(false)}>
                  <span className="ms-icon">{m.icon}</span>
                  <span>{m.label}</span>
                </Link>
              ))}
            </div>
            <Link to="/app/my" className="btn wh sm ms-my" onClick={() => setOpenMore(false)}>
              마이페이지
            </Link>
          </div>
        </>
      )}

      <nav className="mtab" aria-label="하단 탭">
        {TABS.map((t) => (
          <NavLink key={t.to} to={t.to} end={t.to === "/app"} className={({ isActive }) => `mtab-item ${isActive ? "on" : ""}`}>
            <span className="mt-icon">{t.icon}</span>
            <span className="mt-label">{t.label}</span>
          </NavLink>
        ))}
        {/* 더보기 — 시트가 열려 있거나 탭에 없는 화면이면 활성 표시 */}
        <button type="button"
          className={`mtab-item ${openMore || MORE.some((m) => loc.pathname.startsWith(m.to)) ? "on" : ""}`}
          onClick={() => setOpenMore((v) => !v)}>
          <span className="mt-icon">☰</span>
          <span className="mt-label">더보기</span>
        </button>
      </nav>
    </>
  );
}
