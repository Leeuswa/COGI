/*
 * API 클라이언트 — API 명세서(API-001 ~ API-064)와 1:1 대응.
 *
 * 백엔드 연동 방법:
 *  1) USE_MOCK 을 false 로 바꾼다.
 *  2) vite.config.js 의 proxy 주석을 푼다. (Spring Boot :8080)
 *  3) 끝. 화면 코드는 손댈 게 없다. (전 페이지가 이 파일만 바라봄)
 *
 * 목 모드일 때는 data/mock.js 의 1건짜리 데이터를 살짝 지연시켜 내려준다.
 * 실제 네트워크처럼 느껴지게 250ms 정도만.
 */
import * as M from '../data/mock';
import { MODEL_TIERS } from '../data/constants';

const USE_MOCK = false; // 목 데이터 → 실제 백엔드
const BASE = ''; // 프록시 쓰면 '' 그대로, 직접 붙이면 'http://localhost:8080'

// 목 응답 헬퍼. structuredClone으로 원본 오염 방지
// 목/실서버 반환은 일단 any — 화면별 응답 타입은 점진적으로 붙인다 (strict 이행 시)ß
const mock = (data: any, ms = 250): Promise<any> =>
  new Promise((res) => setTimeout(() => res(structuredClone(data)), ms));

// 실서버 호출 헬퍼. JWT는 localStorage에서 꺼내 Authorization 헤더로
// async function http(method: string, path: string, body?: unknown): Promise<any> {
//   const token = localStorage.getItem('cogi-jwt');
//   const res = await fetch(BASE + path, {
//     method,
//     headers: {
//       'Content-Type': 'application/json',
//       ...(token ? { Authorization: `Bearer ${token}` } : {}),
//     },
//     body: body ? JSON.stringify(body) : undefined,
//   });
//   if (!res.ok) throw Object.assign(new Error(`HTTP ${res.status}`), { status: res.status });
//   return res.status === 204 ? null : res.json();
// }

// 실서버 호출. JWT는 HttpOnly 쿠키라 JS가 안 만지고, credentials로 쿠키 자동 전송
async function http(method: string, path: string, body?: unknown): Promise<any> {
  const res = await fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',   // 쿠키 자동 첨부
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) {
    // 세션 만료: 로그인 상태에서 401이 오면 자동 로그아웃(로그인 실패=미로그인 401은 제외)
    if (res.status === 401 && localStorage.getItem('cogi-user')) {
      localStorage.removeItem('cogi-user');
      localStorage.removeItem('cogi-expires');
      window.location.href = '/login?expired=1';
    }
    // 백엔드 {success,message,data}의 message를 꺼내 화면에 그대로 보여줄 수 있게 실어 던진다
    let message = '';
    try { message = (await res.json())?.message ?? ''; } catch { /* 본문 없음/파싱 실패 */ }
    throw Object.assign(new Error(message || `HTTP ${res.status}`), { status: res.status });
  }
  // 본문이 비어 있을 수 있음(204 또는 200 empty, 예: DELETE 해지) → res.json() 대신 text로 받아 방어
  const text = res.status === 204 ? '' : await res.text();
  const json = text ? JSON.parse(text) : null;
  // 백엔드가 {success,message,data}로 감싸므로 data만 꺼내 화면에 전달
  return json && json.data !== undefined ? json.data : json;
}

// multipart(form-data) 전용 — Content-Type은 지정하지 않는다(브라우저가 boundary 포함해서 자동 설정)
async function httpMultipart(path: string, formData: FormData): Promise<any> {
  const res = await fetch(BASE + path, {
    method: 'POST',
    credentials: 'include',
    body: formData,
  });
  if (!res.ok) throw Object.assign(new Error(`HTTP ${res.status}`), { status: res.status });
  const json = res.status === 204 ? null : await res.json();
  return json && json.data !== undefined ? json.data : json;
}

/* ══════════ 회원/인증 (AUTH) ══════════ */

// API-001 GET /oauth2/authorization/github — GitHub OAuth 진입점(리다이렉트)
// SPA에서는 fetch가 아니라 그냥 이동시켜야 해서 URL만 내보낸다.
export const githubOAuthUrl = () => `${BASE}/oauth2/authorization/github`;

// (신규) 카카오 OAuth 진입점 — 요구사항 7번. 백엔드에 카카오 클라이언트 등록 필요
export const kakaoOAuthUrl = () => `${BASE}/oauth2/authorization/kakao`;

// (신규) 소셜 프로필 조회 — OAuth 동의 직후, 아직 가입 전 단계.
// 실서버: 콜백에서 서버가 임시 세션에 담아둔 프로필(닉네임/이메일)을 내려준다.
// 프론트는 이 값을 읽기전용으로 보여주기만 하고, 절대 입력받지 않는다 (카카오 소유 정보).
export const socialProfile = (provider) =>
  USE_MOCK
    ? mock(provider === 'KAKAO'
        ? { provider, name: M.mockKakaoProfile.nickname, email: M.mockKakaoProfile.email }
        : { provider, name: M.mockGithubProfile.githubUsername, email: M.mockGithubProfile.email, githubUsername: M.mockGithubProfile.githubUsername })
    : http('GET', `/api/auth/social/profile?provider=${provider}`);

// (신규) 소셜 가입 확정 — 약관 동의만 받으면 끝. 비밀번호/이메일 인증 없음.
// 이름·이메일은 서버가 OAuth 세션에서 직접 꺼내 저장하므로 body 로 보내지 않는다 (위변조 방지).
export const socialSignup = (provider, agreeTermIds) =>
  USE_MOCK
    ? mock({ ok: true, accessToken: 'mock-jwt',
        user: { ...M.mockUser, onboardingCompleted: false, provider,
          ...(provider === 'KAKAO'
            ? { name: M.mockKakaoProfile.nickname, email: M.mockKakaoProfile.email }
            : { name: M.mockGithubProfile.githubUsername, email: M.mockGithubProfile.email, githubUsername: M.mockGithubProfile.githubUsername }) } })
    : http('POST', '/api/auth/signup/social', { provider, agreeTerms: agreeTermIds });

// (목 전용) 소셜 로그인 흉내 — 실서버는 OAuth 리다이렉트 → /oauth/callback 이라 이 함수를 안 탄다
export const socialLogin = (provider) =>
  USE_MOCK
    ? mock({ accessToken: 'mock-jwt',
        user: { ...M.mockUser, provider,
          ...(provider === 'KAKAO'
            ? { name: M.mockKakaoProfile.nickname, email: M.mockKakaoProfile.email }
            : { name: M.mockGithubProfile.githubUsername, email: M.mockGithubProfile.email, githubUsername: M.mockGithubProfile.githubUsername }) } })
    : Promise.reject(new Error('실서버 소셜 로그인은 리다이렉트 흐름을 사용'));

// API-003 POST /api/auth/signup — 이메일+비밀번호 가입 (약관 동의 포함, FR-89)
// nickname은 선택 — 비우면 백엔드가 이메일 앞부분으로 채운다. 약관 동의 이력은 가입 트랜잭션에서 함께 저장(별도 호출 불필요).
export const signup = (email, password, passwordConfirm, agreeTermIds, nickname) =>
  USE_MOCK
    ? mock({ status: 'EMAIL_PENDING', email, accessToken: 'mock-jwt',
        user: { ...M.mockUser, email, name: nickname || email.split('@')[0], provider: 'EMAIL', onboardingCompleted: false } })
    : http('POST', '/api/auth/signup', { email, password, passwordConfirm, termIds: agreeTermIds, nickname });

// API-004 POST /api/auth/email/send-code — 인증코드 발송 (SIGNUP/FIND_ID/RESET_PW 공용)
export const sendEmailCode = (email, purpose) =>
  USE_MOCK
    ? mock({ sent: true, devHint: '000000' }) // 목 모드 전용: 코드 000000 고정
    : http('POST', '/api/auth/email/send-code', { email, purpose });

// API-005 POST /api/auth/email/verify-code — 코드 검증(5분 만료)
export const verifyEmailCode = (email, code) =>
  USE_MOCK
    ? (code === '000000' ? mock({ verified: true }) : Promise.reject(Object.assign(new Error('코드 불일치'), { status: 400 })))
    : http('POST', '/api/auth/email/verify-code', { email, code });

// API-006 POST /api/auth/login — 아이디(또는 이메일) 로그인. 5회 오류 잠금(423)은 백엔드 소관
// 목: mock/user.js 의 테스트 계정(user/1234, adim/1234)만 통과. 그 외엔 401과 같은 실패
export const login = (loginId, password) => {
  // 백엔드는 이메일로 로그인. 성공 시 JWT는 응답 body가 아니라 HttpOnly 쿠키로 내려온다.
  if (!USE_MOCK) return http('POST', '/api/auth/login', { email: loginId, password });
  const id = String(loginId).trim().toLowerCase(); // 공백/대문자 실수 방어
  const acc = M.mockAccounts.find((a) => a.loginId === id && a.password === String(password).trim());
  if (!acc) return new Promise((_, rej) => setTimeout(() => rej(Object.assign(new Error('401'), { status: 401 })), 300));
  return mock({
    accessToken: 'mock-jwt', expiresIn: 3600, totpRequired: false,
    user: { ...M.mockUser, ...acc, onboardingCompleted: true }, // githubUsername 은 계정별 (user 는 null)
  });
};

// POST /api/auth/logout — 서버가 HttpOnly 쿠키를 만료시켜 세션을 끊는다. (JS로는 못 지우는 쿠키라 서버 호출 필수)
export const logout = () =>
  USE_MOCK ? mock({ ok: true }) : http('POST', '/api/auth/logout');

// POST /api/auth/refresh — 세션(JWT) 연장. 현재 쿠키가 유효할 때 새 토큰 쿠키를 재발급받는다.
export const extendSession = () =>
  USE_MOCK ? mock({ ok: true }) : http('POST', '/api/auth/refresh');

// [설계 추론] 로그인 상태 비밀번호 변경 — 비밀번호 찾기(API-008)와 별개로,
// 마이페이지에서 현재 비밀번호 확인 후 즉시 교체. 백엔드 협의 필요
export const changePassword = (currentPassword, newPassword, newPasswordConfirm) =>
  USE_MOCK
    ? (currentPassword === '1234' // 목: 테스트 계정 비번과 대조
        ? mock({ changed: true })
        : new Promise((_, rej) => setTimeout(() => rej(Object.assign(new Error('401'), { status: 401 })), 300)))
    : http('PATCH', '/api/users/me/password', { currentPassword, newPassword, newPasswordConfirm });

// API-006-1 POST /api/users/me/totp/setup — 로그인 상태에서 시크릿/QR 발급 (아직 활성화 아님)
export const totpSetup = () =>
  USE_MOCK
    ? mock({ secret: 'COGI-MOCK-SECRET', qrCodeUrl: null })
    : http('POST', '/api/users/me/totp/setup');

// POST /api/users/me/totp/enable — 인증앱 6자리 검증 통과 시 최종 활성화
export const totpEnable = (code) =>
  USE_MOCK
    ? (code === '000000' ? mock({ ok: true }) : Promise.reject(new Error('OTP 불일치')))
    : http('POST', '/api/users/me/totp/enable', { code });

// API-006-2 POST /api/auth/totp/verify — 6자리 코드 검증 후 최종 JWT
export const totpVerify = (totpCode, tempToken) =>
  USE_MOCK
    ? (totpCode === '000000' ? mock({ accessToken: 'mock-jwt' }) : Promise.reject(new Error('OTP 불일치')))
    : http('POST', '/api/auth/totp/verify', { totpCode, tempToken });

// API-007 POST /api/auth/password/reset-request — 재설정 토큰 발급
export const passwordResetRequest = (email, verifiedCode) =>
  USE_MOCK ? mock({ resetToken: 'mock-reset' }) : http('POST', '/api/auth/password/reset-request', { email, code: verifiedCode });

// API-008 PATCH /api/auth/password/reset — 새 비밀번호 저장(잠금 해제 포함)
export const passwordReset = (resetToken, newPassword) =>
  USE_MOCK ? mock({ ok: true }) : http('PATCH', '/api/auth/password/reset', { resetToken, newPassword, newPasswordConfirm: newPassword }); // 확인값은 화면에서 이미 일치 검증됨

// API-009 GET /api/users/me/profile — 프로필+현재 플랜
export const getProfile = () =>
  USE_MOCK ? mock(M.mockUser) : http('GET', '/api/users/me/profile').then((u) => ({ ...u, name: u.nickname }));

// FR-91 약관 재동의 필요 여부 — 필수 약관 버전이 올라갔는데 내 동의가 구버전이면 required=true.
// 목 기본값은 false. 배너 테스트: mock/terms.js 의 mockReagreement.required 를 true 로
export const checkReagreement = () =>
  USE_MOCK ? mock(M.mockReagreement) : http('GET', '/api/users/me/terms/reagreement');

// API-010 PATCH /api/users/me/profile — 프로필 수정(즉시 반영, FR-12)
// name(닉네임)은 이메일 가입자만 수정 가능. 소셜(카카오/GitHub) 가입자가 보내면 서버가 400으로 거절해야 한다.
export const updateProfile = (name, level, interests) =>
  USE_MOCK ? mock({ name, level, interests }) : http('PATCH', '/api/users/me/profile', { nickname: name, level, interests });

// API-011 GET /api/users/me/onboarding-status
export const getOnboardingStatus = () =>
  USE_MOCK ? mock({ completed: false }) /* 목: 온보딩 화면에 온 사람은 미완료로 간주 */ : http('GET', '/api/users/me/onboarding-status');

// API-012 POST /api/users/me/onboarding — 최초 1회 설문 제출(스킵 불가, FR-15~17)
export const submitOnboarding = (level, interests, guideConfirmed) =>
  USE_MOCK ? mock({ ok: true }) : http('POST', '/api/users/me/onboarding', { level, interests, guideConfirmed });

// API-013 POST /api/users/me/github-link — 이메일 가입자의 GitHub 추가 연동 (FR-13~14)
export const linkGithub = () =>
  USE_MOCK ? mock({ linked: true, githubUsername: 'fable-dev' }) : http('POST', '/api/users/me/github-link');

// API-063 GET /api/terms — 활성 약관 목록
export const getTerms = () =>
  USE_MOCK ? mock(M.mockTerms) : http('GET', '/api/terms');

// GET /api/users/me/agreements — 내가 동의한 약관 id 목록 (마이페이지 동의 현황)
export const getMyAgreements = () =>
  USE_MOCK ? mock([1, 2]) : http('GET', '/api/users/me/agreements');

// API-064 POST /api/users/me/agreements — 약관 동의 제출
export const submitAgreements = (agreements) =>
  USE_MOCK ? mock({ ok: true }) : http('POST', '/api/users/me/agreements', { agreements });

/* ══════════ PR 리뷰 (REV) ══════════ */

// API-022 GET /api/repos/github — 내 GitHub 레포 목록(private 포함)
export const getGithubRepos = () =>
  USE_MOCK
    ? mock([M.mockRepo, { id: 2, githubRepoId: 'fable/cogi-frontend', repoName: 'cogi-frontend', isPrivate: false, webhookId: null }])
    : http('GET', '/api/repos/github');
// ↑ 미연동 레포 1건은 "연동하기" 버튼 동작 확인용. 연동된 데이터 자체는 mockRepo 1건뿐.

// API-023 POST /api/repos/{repoId}/link — 레포 저장 + Webhook 자동 등록 (FR-27)
export const linkRepo = (repoId) =>
  USE_MOCK ? mock({ webhookId: 'wh_new_' + repoId }) : http('POST', `/api/repos/${repoId}/link`);

// API-025 GET /api/prs/{prId}/review — PR 리뷰 결과(이슈 목록)
export const getPrReview = (prId) =>
  USE_MOCK ? mock({ pr: { ...M.mockPr, myRole: 'OWNER' /* 팀장. 명세 API-034 표기 그대로 (FR-37/44) */ }, issues: [M.mockIssue] }) : http('GET', `/api/prs/${prId}/review`);


// API-029 POST /api/repos/{repoId}/members/invite — GitHub 아이디로 팀원 초대 (FR-36)
// githubUsername으로 못 찾으면(GITHUB_USER_NOT_FOUND) email을 채워 재요청 — 그때만 이메일 초대장이 발송된다
export const inviteMember = (repoId, githubUsername, email?: string) =>
  USE_MOCK ? mock({ inviteId: 1, status: 'PENDING' }) : http('POST', `/api/repos/${repoId}/members/invite`, { githubUsername, email });

// API-031 GET /api/repos/{repoId}/members — 팀 구성원 목록 (성장추이 팀원 필터에서도 씀)
export const getTeamMembers = (repoId) =>
  USE_MOCK ? mock(M.mockRepo.members) : http('GET', `/api/repos/${repoId}/members`);

// API-030 / API-030-1 — 받은 초대 수락/거절 (FR-37). 수락하면 레포 조회 권한이 열린다
export const respondInvite = (repoId, inviteId, accept) =>
  USE_MOCK ? mock({ status: accept ? 'ACCEPTED' : 'REJECTED' })
    : http('POST', `/api/repos/${repoId}/members/invitations/${inviteId}/${accept ? 'accept' : 'reject'}`);

// 내가 받은 대기 중 초대 목록(인앱 알림)
export const getMyInvitations = () =>
  USE_MOCK ? mock(M.mockInvitations) : http('GET', '/api/me/repo-invitations');

// 팀원이 스스로 팀 나가기 — 팀장은 먼저 위임해야 함(OWNER_CANNOT_LEAVE)
export const leaveRepo = (repoId) =>
  USE_MOCK ? mock({ ok: true }) : http('POST', `/api/repos/${repoId}/members/leave`);

// 팀장이 팀원 내보내기
export const removeTeamMember = (repoId, targetUserId) =>
  USE_MOCK ? mock({ ok: true }) : http('DELETE', `/api/repos/${repoId}/members/${targetUserId}`);

// 팀장 위임 — 기존 팀장은 MEMBER로 강등, 대상은 OWNER로 승격
export const transferOwnership = (repoId, targetUserId) =>
  USE_MOCK ? mock({ ok: true }) : http('POST', `/api/repos/${repoId}/members/${targetUserId}/transfer-owner`);

/* ══════════ 리뷰 대시보드 (RDB) ══════════ */

// API-032 GET /api/repos/{repoId}/prs/reviewed — PR 리뷰 대시보드(팀 대신 레포 기준으로 축소) [설계 추론]
// 원래 명세는 팀 단위(teamId)였지만 백엔드에 "팀" 개념이 따로 없어(레포+팀원이 곧 팀) 레포 기준으로 구현
export const getRepoReviewedPrs = (repoId) =>
  USE_MOCK ? mock([M.mockPr]) : http('GET', `/api/repos/${repoId}/prs/reviewed`);

// API-031 PATCH /api/issues/{issueId}/acknowledge — 의도한 코드 응답 (FR-40)
// [설계 추론] 스튜디오 판정 확정 — 의도(IGNORED)는 바로 반영. 고침(RESOLVED)은 PR 리뷰의 CRITICAL
// 이슈면 서버가 승인 대기(PENDING)로 돌려놓는다(요구 #7 + RDB-003)
export const finalizeIssue = (issueId, verdict) =>
  USE_MOCK ? mock({ ok: true, status: verdict }) : http('PATCH', `/api/issues/${issueId}/finalize`, { verdict });

// [설계 추론] 이슈 승인 흐름(RDB-003, API-034) — 팀장만 호출 가능. approve=false면 반려(OPEN으로 되돌림)
export const decideResolveRequest = (issueId, approve) =>
  USE_MOCK ? mock({ ok: true }) : http('PATCH', `/api/issues/${issueId}/decision`, { approve });

// API-035 GET /api/prs/{prId}/export — MD/TXT 내보내기. 목 모드는 프론트에서 파일을 만들어 내려준다
export const exportReview = (prId, format) =>
  USE_MOCK ? mock({ ok: true }) : http('GET', `/api/prs/${prId}/export?format=${format}`);

// API-036 / API-037 — Notion / PDF (MVP 이후 검토 항목)
export const exportNotion = (prId, workspaceId) =>
  USE_MOCK ? mock({ notionPageUrl: null, pending: true }) : http('POST', `/api/prs/${prId}/export/notion`, { workspaceId });
export const exportPdf = (prId) =>
  USE_MOCK ? mock({ pending: true }) : http('POST', `/api/prs/${prId}/export/pdf`);

// API-038 GET /api/prs/{prId}/preview — 프론트엔드 변경 실시간 미리보기 (FR-47~49)
// ponytail: 명세상 [불확실]·MVP 제외 후보라 404 응답(미지원 사유)만 다루는 스텁. WebContainer 붙일 때 확장

// 스튜디오 'PR 가져오기' 피커 1단계 — 레포의 열린 PR 목록(GitHub 실시간 조회)
export const getRepoPrs = (repoId) =>
  USE_MOCK ? mock([{ number: 42, title: '샘플 PR', authorLogin: 'octocat' }])
    : http('GET', `/api/repos/${repoId}/prs`);

// 스튜디오 'PR 가져오기' 피커 2단계 — 특정 PR의 변경 파일 목록(코드는 diff 근사치)
export const getRepoPrFiles = (repoId, prNumber) =>
  USE_MOCK ? mock(M.mockPrFiles) : http('GET', `/api/repos/${repoId}/prs/${prNumber}/files`);

// 스튜디오 'PR 가져오기' 피커 3단계(리뷰 실행) [설계 추론] — target_type=PR로 저장돼 PR 리뷰 목록에도 뜬다
export const reviewImportedPr = (repoId, prNumber, code, modelName, title, authorLogin) =>
  USE_MOCK
    ? mock({ reviewId: 4, modelName: modelName || 'claude-haiku-4-5', status: 'COMPLETED',
        summary: null, analyzable: true, issues: [{ ...M.mockIssue, id: 4 }] }, 900)
    : http('POST', `/api/repos/${repoId}/prs/${prNumber}/review`, { code, modelName, title, authorLogin });

/* ══════════ 비로그인 / 직접 업로드 (LOC) ══════════ */

// API-039 POST /api/guest/local-review — 비로그인 즉시 리뷰(쿠키 3회 제한, FR-50~52)
// 목 모드의 3회 카운트는 GuestReview 페이지에서 localStorage로 처리한다.
// level: 게스트가 고른 코딩 수준 — 관리자 지침(ADM-004)에 맞춰 설명 깊이가 달라진다
export const guestReview = (code, level = 'BEGINNER') => {
  if (!USE_MOCK) return http('POST', '/api/guest/local-review', { code, level });
  // 수준별 설명 — 관리자 지침(ADM-004)이 실제로 하는 일을 목으로 재현
  const DESC = {
    BEGINNER: 'user 가 비어있을 수 있어요. 비어있는 상자에서 물건(name)을 꺼내려 하면 앱이 멈춥니다. user?.name 처럼 물음표를 붙이면 "상자가 비었으면 그냥 넘어가"라는 뜻이 돼요.',
    INTERMEDIATE: 'user 객체 null 체크 누락. 옵셔널 체이닝(user?.name)으로 방어하거나, 함수 시작부에 가드절을 두는 게 좋습니다.',
    ADVANCED: 'null 유입 지점이 상위 호출부에 있을 가능성이 큽니다. 타입 레벨에서 User | null 을 분리하고, 경계에서 내로잉해 이 함수는 non-null 계약으로 두는 편이 방어 코드 반복보다 낫습니다.',
  };
  return mock({
    reviewId: 9, guestToken: 'mock-guest-token',
    issues: [{ ...M.mockIssue, filePath: '(붙여넣은 코드)', description: DESC[level] ?? DESC.BEGINNER }],
    remaining: 2,
  });
};

// [설계 추론] 리뷰 후속 질문 — 대화형 리뷰 UX용. 직전 리뷰 맥락으로 짧은 답을 받는다
export const askReviewQuestion = (reviewId, question) =>
  USE_MOCK
    ? mock({ answer: `좋은 질문이에요! "${question.slice(0, 24)}" 관련해서: 옵셔널 체이닝(user?.email)을 쓰면 user가 null이어도 undefined로 안전하게 흘러가요. 다만 "왜 null이 오는지"를 찾아 근본 원인을 막는 게 더 중요합니다.` }, 800)
    : http('POST', `/api/reviews/${reviewId}/questions`, { question });

// API-039-1 POST /api/guest/local-review/{reviewId}/claim — 가입/로그인 직후 게스트 리뷰를 내 계정으로 매핑
// AuthContext.signIn 한 곳에서만 부른다(이메일/카카오/GitHub 어느 경로든 로그인은 거길 지나므로).
export const claimGuestReview = (reviewId, guestToken) =>
  USE_MOCK ? mock({ claimed: true }) : http('POST', `/api/guest/local-review/${reviewId}/claim`, { guestToken });

// API-040 POST /api/reviews/paste — 로그인 후 붙여넣기 리뷰 (LRN-001 통계 반영 대상)
// language는 스튜디오 UI에서 뺐다 — 코드만 붙여넣으면 AI가 알아서 언어를 판단함(_common_rules.txt 1번 규칙),
// 백엔드 DTO의 language 필드는 nullable이라 그대로 생략해서 보낸다.
export const pasteReview = (code, modelName) =>
  USE_MOCK
    ? mock({ reviewId: 2, issues: [{ ...M.mockIssue, id: 2, filePath: '(붙여넣은 코드)' }] }, 900)
    : http('POST', '/api/reviews/paste', { code, modelName });

// API-041 POST /api/reviews/upload — 파일 업로드 리뷰
export const uploadReview = (file, modelName?: string) => {
  if (USE_MOCK) return mock({ reviewId: 3, issues: [{ ...M.mockIssue, id: 3, filePath: file.name }] }, 900);
  const fd = new FormData();
  fd.append('file', file);
  if (modelName) fd.append('modelName', modelName);
  return httpMultipart('/api/reviews/upload', fd);
};

// API-027 GET /api/reviews/model-options — 내 요금제에서 선택 가능한 모델 이름 목록
export const getModelOptions = () =>
  USE_MOCK ? mock(MODEL_TIERS.map((m) => m.name)) : http('GET', '/api/reviews/model-options');

// [설계 추론] GET /api/reviews/history — 붙여넣기/업로드/PR가져오기로 만든 개별 리뷰 목록(최신순)
// "약점 통계"는 3회 이상 반복된 카테고리만 집계해서 보여주는 화면이라, 리뷰 1건 단위로 다시 찾아보려면 이게 필요하다.
export const getReviewHistory = () =>
  USE_MOCK
    ? mock([{ reviewId: 2, targetType: 'PASTE', modelName: 'claude-haiku-4-5', status: 'COMPLETED',
        originalFilename: null, createdAt: '2026-07-18T10:00:00', issueCount: 1, topSeverity: 'CRITICAL' }])
    : http('GET', '/api/reviews/history');

// [설계 추론] GET /api/reviews/history/{reviewId} — 히스토리 상세(이슈 목록)
export const getReviewHistoryDetail = (reviewId) =>
  USE_MOCK
    ? mock({ reviewId, targetType: 'PASTE', modelName: 'claude-haiku-4-5', status: 'COMPLETED',
        originalFilename: null, createdAt: '2026-07-18T10:00:00', issues: [M.mockIssue] })
    : http('GET', `/api/reviews/history/${reviewId}`);

/* ══════════ 맞춤 학습 (LRN) ══════════ */

// API-042 GET /api/users/me/weakness-stats — 약점 통계(3회 이상). 데이터 없으면 콜드스타트 (FR-57)
export const getWeaknessStats = () =>
  USE_MOCK ? mock(M.mockWeaknessList) : http('GET', '/api/users/me/weakness-stats');

// API-043 POST /api/learning-cards — 카드 생성(개념+예제+퀴즈, 수준/언어 반영)
export const createLearningCard = (category, level, language) =>
  USE_MOCK ? mock({ ...M.mockCard, category, level, language }, 900) : http('POST', '/api/learning-cards', { category, level, language });

// API-044 GET /api/learning-cards/{cardId}
export const getLearningCard = (cardId) =>
  USE_MOCK ? mock(M.mockCard) : http('GET', `/api/learning-cards/${cardId}`);

// (목록 조회 — 명세엔 상세만 있지만 목록 화면이 필요해서 추가. 백엔드에 GET /api/learning-cards 요청해두자)
export const getLearningCards = () =>
  USE_MOCK ? mock([M.mockCard]) : http('GET', '/api/learning-cards');

// API-044-1 / 044-2 — 북마크 / 완료 토글
export const toggleBookmark = (cardId, bookmarked) =>
  USE_MOCK ? mock({ bookmarked }) : http('PATCH', `/api/learning-cards/${cardId}/bookmark`, { bookmarked });
// API-044-2 PATCH /api/learning-cards/{cardId}/complete — 완료 체크 토글 (신호등 등급과 별개)
export const toggleComplete = (cardId, completed) =>
  USE_MOCK ? mock({ completed }) : http('PATCH', `/api/learning-cards/${cardId}/complete`, { completed });

// API-045 POST /api/learning-cards/{cardId}/quiz — 문제 생성(크레딧 소진 시 402)
// questionType: MULTIPLE_CHOICE / OX / SHORT_ANSWER / FILL_BLANK (FR-64)
export const createQuiz = (cardId, questionType = 'MULTIPLE_CHOICE') =>
  USE_MOCK
    ? mock(M.mockQuizzes.find((q) => q.questionType === questionType) ?? M.mockQuizzes[0], 700)
    : http('POST', `/api/learning-cards/${cardId}/quiz`, { questionType });

// API-046 POST .../quiz/{quizId}/submit — 정답 제출 → 등급 승급 + streak 갱신 (RET-001 통합)
export const submitQuiz = (cardId, quizId, answer) => {
  if (!USE_MOCK) return http('POST', `/api/learning-cards/${cardId}/quiz/${quizId}/submit`, { answer });
  const right = M.mockQuizzes.find((q) => q.id === quizId)?.answer ?? '';
  const isCorrect = String(answer).trim() === right; // 주관식은 공백만 정리해서 비교
  return mock({ isCorrect, streakUpdated: true });
};

// API-047 GET .../history — 승급 히스토리 (FR-63)
export const getCardHistory = (cardId) =>
  USE_MOCK ? mock(M.mockCard.history) : http('GET', `/api/learning-cards/${cardId}/history`);

// API-048 GET /api/courses/recommendations — 강의 추천 + 필터 (FR-66~69)
export const getCourses = (filters = {}) =>
  USE_MOCK ? mock([M.mockCourse]) : http('GET', '/api/courses/recommendations?' + new URLSearchParams(filters));

// API-049 POST /api/ai-skill-recommendations — AI 스킬 추천 (FR-70, 크레딧 소모)
export const recommendSkill = (weaknessOrRequirement) =>
  USE_MOCK
    ? mock({
        result:
          '입력하신 약점 기준으로 추천드려요.\n\n' +
          '① Claude Code + 정적분석 스킬 — PR 올리기 전 자가 점검\n' +
          '② ESLint strict-null 룰셋 — 저장할 때마다 자동으로 잡아줌\n' +
          '③ COGI 학습카드 — "null 안전성" 카드 반복 풀이',
      }, 1100)
    : http('POST', '/api/ai-skill-recommendations', { weaknessOrRequirement });

/* ══════════ 성장/리텐션 (GRW/RET) ══════════ */

// API-050 GET /api/teams/{teamId}/growth-trend — 기간별 이슈 빈도 (FR-71~72)
export const getGrowthTrend = (teamId, params = {}) =>
  USE_MOCK ? mock(M.mockGrowth) : http('GET', `/api/teams/${teamId}/growth-trend?` + new URLSearchParams(params));

// API-051 GET /api/users/me/retention-status — streak/오늘 제출 여부
// 목 모드에선 GameContext(localStorage)가 원천이라 이 API는 실서버 전환용 자리만 잡아둠
export const getRetentionStatus = () =>
  USE_MOCK ? mock({ currentStreak: 0, submittedToday: false }) : http('GET', '/api/users/me/retention-status');

/* ══════════ 크레딧/구독/결제 (SYS/SUB/PAY) ══════════ */

// API-055 GET /api/users/me/credit-usage — 일일 크레딧 사용량 (90%/100% 알림 판단)
export const getCreditUsage = () =>
  USE_MOCK ? mock({ usedCredits: 0, dailyLimit: 20, remaining: 20 }) : http('GET', '/api/users/me/credit-usage');

// API-058 GET /api/users/me/plan — 내 현재 플랜/크레딧 한도 (users.plan_id 캐시 기준)
export const getMyPlan = () =>
  USE_MOCK ? mock({ planName: M.mockUser.planName, dailyCreditLimit: 20 }) : http('GET', '/api/users/me/plan');

// API-057 GET /api/plans — 요금제 목록
export const getPlans = () =>
  USE_MOCK ? mock(M.mockPlans) : http('GET', '/api/plans');

// API-059 POST /api/payments/methods — 결제수단 등록(토스 SDK 결제창 방식)
// arg = { authKey, customerKey } — 토스 결제창 성공 콜백에서 받은 값. 백엔드가 authKey로 빌링키를 발급·저장.
// 응답은 paymentMethodId(숫자)만 돌려준다.
export const registerPaymentMethod = (arg) =>
  USE_MOCK ? mock(1) : http('POST', '/api/payments/methods', arg);

// API-060 POST /api/subscriptions — 구독 시작
export const startSubscription = (planId, paymentMethodId, agreeTermIds) =>
  USE_MOCK ? mock({ subscriptionId: 1 }) : http('POST', '/api/subscriptions', { planId, paymentMethodId, agreeTerms: agreeTermIds });

// API-061 PATCH /api/subscriptions/{subId} — 플랜 전환(같은 구독 건에서 plan_id만 교체, FR-87)
export const changePlan = (subId, newPlanId) =>
  USE_MOCK ? mock({ ok: true, proratedAmount: 3300 }) : http('PATCH', `/api/subscriptions/${subId}`, { newPlanId });

// API-062 DELETE /api/subscriptions/{subId} — 해지(예약)
export const cancelSubscription = (subId) =>
  USE_MOCK ? mock({ ok: true }) : http('DELETE', `/api/subscriptions/${subId}`);

// 해지 예약 취소 — cancelledAt 초기화, 구독 유지
export const resumeSubscription = (subId) =>
  USE_MOCK ? mock({ ok: true }) : http('POST', `/api/subscriptions/${subId}/resume`);

// (조회) 구독 변경 이력 — subscription_history
export const getSubHistory = () =>
  USE_MOCK ? mock([M.mockSubHistory]) : http('GET', '/api/users/me/subscription-history');

/* ══════════ 관리자 (ADM) ══════════ */

// [설계 변경] 주간 리포트 — RET-002/FR-75 의 '자동 메일 발송'을
// '탭에서 조회 + 원할 때 메일 발송'으로 변경. 배치는 리포트 생성까지만 담당한다.
export const getWeeklyReports = () =>
  USE_MOCK ? mock(M.mockWeeklyReports) : http('GET', '/api/users/me/weekly-reports');
export const sendWeeklyReportMail = (reportId) =>
  USE_MOCK ? mock({ sent: true }) : http('POST', `/api/users/me/weekly-reports/${reportId}/send-mail`);

// 내가 이미 연동해둔 레포 목록 — 팀 화면(TeamPage)이 "내 팀 = 내가 연동한 레포"를 보여줄 때 씀
export const getMyLinkedRepos = () =>
  USE_MOCK ? mock([{ repoId: 1, repoName: M.mockRepo.repoName }]) : http('GET', '/api/repos/me');

// [설계 추론] 알림 — GitHub/이메일 초대가 오면 종 아이콘에 쌓인다 (삭제는 프론트 로컬)
export const getNotifications = (loginId) =>
  USE_MOCK ? mock(M.mockNotifications[loginId] ?? []) : http('GET', '/api/notifications');
export const dismissNotification = (id) =>
  USE_MOCK ? mock({ ok: true }) : http('DELETE', `/api/notifications/${id}`);

// API-054 GET /api/admin/system/review-latency — 리뷰 응답시간 지표 (FR-77, 목표 수치는 문서상 미확정)
export const getReviewLatency = (from, to) =>
  USE_MOCK ? mock({ avgSec: 42, p95Sec: 88, targetSec: 60 }) : http('GET', `/api/admin/system/review-latency?from=${from}&to=${to}`);

// API-014 GET /api/admin/ai-usage — 토큰 사용량/비용 (403은 백엔드 @PreAuthorize)
export const adminAiUsage = (from, to) =>
  USE_MOCK ? mock([M.mockUsageLog]) : http('GET', `/api/admin/ai-usage?from=${from}&to=${to}`);

// API-015 GET /api/admin/members
export const adminMembers = () =>
  USE_MOCK ? mock([{ ...M.mockUser, onboardingCompleted: true }]) : http('GET', '/api/admin/members');

// API-016 / API-017 — 상태/권한 변경(즉시 반영, FR-21)
export const adminChangeStatus = (userId, status) =>
  USE_MOCK ? mock({ ok: true }) : http('PATCH', `/api/admin/members/${userId}/status`, { status });
export const adminChangeRole = (userId, role) =>
  USE_MOCK ? mock({ ok: true }) : http('PATCH', `/api/admin/members/${userId}/role`, { role });

// API-018 GET /api/admin/activity-logs
export const adminActivityLogs = () =>
  USE_MOCK ? mock([M.mockActivityLog]) : http('GET', '/api/admin/activity-logs');

// API-019 POST /api/admin/notices/email — 전체 공지 발송
export const adminSendNotice = (subject, content) =>
  USE_MOCK ? mock({ successCount: 1, failCount: 0 }) : http('POST', '/api/admin/notices/email', { subject, content });

// API-020 / API-021 — 수준별 리뷰 지침 조회/저장 (FR-24~25)
export const adminGetGuidelines = () =>
  USE_MOCK ? mock(M.mockGuidelines) : http('GET', '/api/admin/review-guidelines');
export const adminSaveGuideline = (level, promptGuideline) =>
  USE_MOCK ? mock({ ok: true }) : http('PUT', `/api/admin/review-guidelines/${level}`, { promptGuideline });

/* ══════════ 캘린더 연동 (요구사항 8 — 학습카드 → 일정 등록) ══════════ */
// 구글/애플은 백엔드 없이도 진짜로 동작한다. 카카오만 서버 연동 대기.

// 구글 캘린더: 공식 URL 템플릿으로 새 탭 열기
export function googleCalendarUrl(card, date) {
  const d = date.replaceAll('-', '');
  const p = new URLSearchParams({
    action: 'TEMPLATE',
    text: `[COGI] ${card.category} 학습 (5분 미션)`,
    dates: `${d}T090000/${d}T091500`,
    details: `약점카드 학습: ${card.conceptContent.slice(0, 80)}…\n(COGI 학습 가이드에서 자동 생성)`,
  });
  return 'https://calendar.google.com/calendar/render?' + p.toString();
}

// 애플 캘린더: .ics 파일을 만들어 다운로드 (아웃룩도 이걸로 열림)
export function downloadIcs(card, date) {
  const d = date.replaceAll('-', '');
  const ics = [
    'BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//COGI//KR',
    'BEGIN:VEVENT',
    `DTSTART:${d}T090000`,
    `DTEND:${d}T091500`,
    `SUMMARY:[COGI] ${card.category} 학습 (5분 미션)`,
    `DESCRIPTION:약점카드 학습 — COGI 학습 가이드 자동 생성`,
    'END:VEVENT', 'END:VCALENDAR',
  ].join('\r\n');
  const url = URL.createObjectURL(new Blob([ics], { type: 'text/calendar' }));
  const a = document.createElement('a');
  a.href = url; a.download = `cogi-mission-${date}.ics`; a.click();
  URL.revokeObjectURL(url);
}

// 카카오 캘린더: REST API(POST /v2/api/calendar/create/event)가 필요해서 백엔드 경유.
// 카카오 비즈니스 인증 완료 전까지는 대기 상태로 안내만 한다. (약관 문서의 카카오 보류와 같은 사유)
export const kakaoCalendarAdd = (card, date) =>
  USE_MOCK ? mock({ pending: true }) : http('POST', '/api/calendar/kakao', { cardId: card.id, date });
