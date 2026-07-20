// 회원/인증 — Login·Signup·MyPage·Onboarding 화면용 목 데이터
// ── users (테이블 정의서 [회원/인증] users) ──
// name/provider/kakaoId 는 카카오 가입 대응으로 추가한 컬럼.
// 백엔드 users 테이블에도 nickname(VARCHAR), provider(EMAIL/KAKAO/GITHUB), kakao_id 컬럼 추가 필요.
export const mockUser = {
  id: 1,
  email: "dev@fable.dev",
  name: "404개발자", // 닉네임. 이메일 가입자는 마이페이지에서 수정 가능
  provider: "EMAIL", // 가입 경로. KAKAO/GITHUB 면 이름·이메일이 소셜 소유라 수정 잠금
  kakaoId: null,
  githubUsername: null, // GitHub 미연동 상태로 시작 → 마이페이지에서 연동 테스트
  level: null, // BEGINNER / INTERMEDIATE / ADVANCED — 온보딩 전이라 null
  interests: [], // 온보딩에서 채움
  onboardingCompleted: false, // false면 라우터가 /onboarding 으로 강제 이동 (FR-15 스킵불가)
  guideConfirmed: false,
  totpEnabled: false,
  role: "USER", // ADMIN 으로 바꾸면 관리자 메뉴가 열림
  status: "ACTIVE",
  planName: "FREE",
};

// ── 소셜 가입 목 프로필 (각 1건) ──
// 실서버에선 OAuth 콜백 뒤 서버 세션에 담긴 프로필을 내려주므로 이 객체는 안 쓰인다.
// 카카오 동의항목: profile_nickname + account_email 기준.
export const mockKakaoProfile = {
  kakaoId: "kakao-30217745",
  nickname: "코기집사",
  email: "corgi.lover@kakao.mock",
};
export const mockGithubProfile = {
  githubId: "gh-8842167",
  githubUsername: "fable-dev",
  email: "dev@github.mock",
};

// ── 테스트 계정 (목 로그인 전용) — 화면 확인 목적별 4종 ──
// team   : 팀장 화면 (팀 페이지 OWNER 뷰)
// member : 팀원 화면 (팀 페이지 MEMBER 뷰)
// user   : GitHub 연동 안 된 일반 계정 (연동 버튼·잠금 메뉴 확인용)
// admin  : 관리자 콘솔
export const mockAccounts = [
  {
    loginId: "team",
    password: "1234",
    role: "USER",
    name: "정상연(팀장)",
    email: "dev@fable.dev",
    githubUsername: "fable-dev",
  },
  {
    loginId: "member",
    password: "1234",
    role: "USER",
    name: "이수환",
    email: "su@fable.dev",
    githubUsername: "su-dev",
  },
  {
    loginId: "user",
    password: "1234",
    role: "USER",
    name: "테스트유저",
    email: "user@test.dev",
    githubUsername: null,
  },
  {
    loginId: "admin",
    password: "1234",
    role: "ADMIN",
    name: "관리자",
    email: "admin@fable.dev",
    githubUsername: "fable-admin",
  },
];
