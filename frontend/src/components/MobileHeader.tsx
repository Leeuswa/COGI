/*
 * 모바일 전용 앱 헤더.
 * 데스크톱 GNB는 링크가 15개라 폰에 안 들어간다. 모바일에서는 이걸로 통째로 바꾼다.
 *   왼쪽  탭 화면이면 로고, 그 외에는 뒤로가기
 *   가운데 현재 화면 이름
 *   오른쪽 코인·크레딧
 */
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { useGame } from "../context/GameContext";

// 경로 → 화면 이름. 하단 탭에 있는 4개는 뒤로가기 대신 로고를 띄운다
const TITLES: [string, string][] = [
  ["/app/weakness", "약점 통계"],
  ["/app/cards", "학습카드"],
  ["/app/courses", "강의 추천"],
  ["/app/skills", "AI 스킬 추천"],
  ["/app/growth", "성장 추이"],
  ["/app/reports", "주간 리포트"],
  ["/app/history", "리뷰 히스토리"],
  ["/app/paste", "리뷰 스튜디오"],
  ["/app/prs", "PR 리뷰"],
  ["/app/repos", "레포 연동"],
  ["/app/team", "팀"],
  ["/app/plan", "요금제"],
  ["/app/faq", "FAQ"],
  ["/app/my", "마이페이지"],
  ["/app/admin", "관리자"],
];

const ROOT_TABS = ["/app", "/app/prs", "/app/cards", "/app/growth"];

export default function MobileHeader() {
  const { user } = useAuth();
  const { S, creditLimit } = useGame();
  const loc = useLocation();
  const nav = useNavigate();

  if (!user) return null;

  // 더 긴 경로가 먼저 잡히도록 길이순으로 찾는다 (/app/cards/3 → 학습카드)
  const title =
    [...TITLES].sort((a, b) => b[0].length - a[0].length)
      .find(([p]) => loc.pathname.startsWith(p))?.[1] ?? "대시보드";
  const isRoot = ROOT_TABS.includes(loc.pathname);

  return (
    <header className="mhead">
      {isRoot ? (
        <Link to="/app" className="mh-logo"><img src="/logo.png" alt="COGI" /></Link>
      ) : (
        <button type="button" className="mh-back" onClick={() => nav(-1)} aria-label="뒤로">←</button>
      )}

      <h1 className="mh-title">{title}</h1>

      <span className="mh-wallet" title={`코인 ${S.coins}개 · 남은 크레딧 ${creditLimit - S.creditUsed}개`}>
        <b>🪙 {S.coins}</b>
        <b>⚡ {creditLimit - S.creditUsed}</b>
      </span>
    </header>
  );
}
