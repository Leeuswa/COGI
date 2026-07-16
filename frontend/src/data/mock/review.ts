// PR 리뷰 — Repos·PrList·PrDetail·PasteReview·GuestReview 화면용 목 데이터
// ── repo_invitations (FR-37, API-030/030-1) — 내가 받은 대기 중 초대 1건 ──
export const mockInvitations = [
  { inviteId: 1, repoId: 2, repoName: 'fable/cogi-backend', inviterGithub: 'corgi-lead', status: 'PENDING' },
];

// ── repositories (REV-001) ──
export const mockRepo = {
  id: 1,
  githubRepoId: 'fable/cogi-backend',
  repoName: 'cogi-backend',
  isPrivate: true,
  webhookId: 'wh_20260710_001', // 연동 저장 시 자동 등록 (FR-27)
  linkedAt: '2026-07-08T14:20:00',
  // 팀 구성원 (repo_members) — 팀장/팀원 권한 구분(FR-37)과 성장추이 팀원 필터(FR-72)에서 씀.
  // 권한 테스트에 두 역할이 다 필요해서 예외적으로 2건.
  members: [
    { userId: 1, githubUsername: 'fable-dev', role: 'OWNER' },
    { userId: 2, githubUsername: 'corgi-junior', role: 'MEMBER' },
  ],
};

// ── pull_requests (REV-002~003) ──
export const mockPr = {
  id: 42,
  repoId: 1,
  githubPrNumber: 42,
  title: 'feat: 로그인 실패 카운트 잠금 처리',
  authorId: 1,
  authorName: 'dev@fable.dev',
  selectedModel: null,           // null이면 기본 모델 (FR-35)
  status: 'REVIEWED',            // OPEN / REVIEWED / FAILED
  issueCount: 1,
  topSeverity: 'CRITICAL',
  createdAt: '2026-07-10T02:47:00',
};

// ── review_issues (RDB-001/003) — 랜딩 데모 패널의 그 이슈 하나 ──
export const mockIssue = {
  id: 1,
  reviewId: 1,
  prId: 42,
  category: 'BUG',               // BUG / PERFORMANCE / CODE_SMELL / CONVENTION / SECURITY
  severity: 'CRITICAL',          // CRITICAL / MAJOR / MINOR
  filePath: 'src/auth/user.ts',
  lineNumber: 88,
  description: 'user 객체 null 체크 누락. user?.name 또는 가드절로 방어 필요. 이 패턴이 3회째 반복되어 약점(null 안전성)으로 승격되었습니다.',
  status: 'OPEN',                // OPEN / PENDING / RESOLVED / IGNORED
  acknowledged: false,           // 의도한 코드 버튼 (FR-40)
  codeSnippet: [
    '  86 | function greet(user) {',
    '  87 |   // TODO: 새벽에 급하게 짬',
    '  88 |   return `hi, ${user.name}`;',
    '  89 | }',
  ],
};

// ── PR 변경 파일 (스튜디오 'PR 가져오기'용, FR-38/47) ──
// 백엔드·프론트가 섞인 구성 — 리뷰는 전부 가능, 라이브 미리보기는 HTML/CSS/JS 파일만 열린다
export const mockPrFiles = [
  {
    path: 'src/main/java/auth/LoginService.java', language: 'Java', kind: 'backend',
    code: `public String login(String email, String pw) {
    User user = repo.findByEmail(email);
    return jwt.issue(user.getId()); // user null 체크 없음
}`,
  },
  {
    path: 'src/api/auth.ts', language: 'TypeScript', kind: 'backend',
    code: `export async function login(email: string, pw: string) {
  const res = await fetch('/api/auth/login', { method: 'POST', body: JSON.stringify({ email, pw }) });
  return res.json(); // res.ok 확인 없이 파싱
}`,
  },
  {
    path: 'src/pages/welcome.html', language: 'HTML', kind: 'frontend',
    code: `<div style="padding:30px;font-family:sans-serif">
  <h2 id="title">가입 완료!</h2>
  <p>코기가 당신의 코드를 기다립니다.</p>
  <button id="cta" style="padding:12px 24px;background:#ff6b57;color:#fff;border:none;font-size:15px">
    시작하기
  </button>
</div>`,
  },
];
