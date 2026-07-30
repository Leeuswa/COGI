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
import MobileBell from "./MobileBell";

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

// 로그인 전 화면. 랜딩(/)은 목록에 없다 — 제목 없이 로고만 띄운다
const GUEST_TITLES: [string, string][] = [
  ["/login", "로그인"],
  ["/signup", "회원가입"],
  ["/find-password", "비밀번호 찾기"],
  ["/otp", "2차 인증"],
  ["/guest", "체험 리뷰"],
  ["/repo-invites/accept", "초대 수락"],
];

export default function MobileHeader() {
  const { user } = useAuth();
  const { S, creditLimit } = useGame();
  const loc = useLocation();
  const nav = useNavigate();

  // 로그인 전에는 데스크톱 GNB가 폰에서 숨겨져 상단이 통째로 비어 있었다.
  // 지갑·종 대신 로고(또는 뒤로)와 가입·로그인 진입만 남긴다
  if (!user) {
    const title = GUEST_TITLES.find(([p]) => loc.pathname.startsWith(p))?.[1];
    const [cta, ctaLabel] = loc.pathname.startsWith("/login")
      ? ["/signup", "회원가입"]
      : ["/login", "로그인"];
    return (
      <header className="mhead">
        {title ? (
          <button type="button" className="mh-back" aria-label="뒤로"
            /* 주소로 바로 들어오면 이전 기록이 없어 뒤로가기가 사이트 밖으로 나간다 */
            onClick={() => (loc.key === "default" ? nav("/") : nav(-1))}>←</button>
        ) : (
          <Link to="/" className="mh-logo"><img src="/logo.png" alt="COGI" /></Link>
        )}
        <h1 className="mh-title">{title ?? ""}</h1>
        <Link className="btn wh sm mh-cta" to={cta}>{ctaLabel}</Link>
      </header>
    );
  }

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

      {/* 데스크톱 GNB의 종을 폰에서도 유지한다 — 학습 계획 알림이 여기로 온다 */}
      <MobileBell loginId={user.loginId} />
    </header>
  );
}
