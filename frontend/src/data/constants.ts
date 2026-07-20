/*
 * 도메인 상수 — 기능정의서에 박힌 enum이라 백엔드 연동 후에도 그대로 쓴다.
 * (목 데이터가 아니므로 mock 폴더를 지워도 여긴 남는다)
 */
// ── 관심기술 선택지 (온보딩/프로필 공용 상수 — 데이터라기보단 설정값) ──
// 관심기술 — 분야 탭으로 나눠 보여주고, 선택은 분야를 넘나들며 섞을 수 있다
export const INTEREST_GROUPS = {
  '백엔드':   ['Java', 'Spring', 'Kotlin', 'Python', 'Django', 'FastAPI', 'Go', 'Node.js', 'NestJS', 'C#', '.NET', 'Rust'],
  '프론트엔드': ['JavaScript', 'TypeScript', 'React', 'Next.js', 'Vue', 'Svelte', 'HTML/CSS', 'Tailwind', 'React Native', 'Flutter'],
  'DB':      ['SQL', 'MySQL', 'PostgreSQL', 'MongoDB', 'Redis', 'Oracle', 'Elasticsearch', 'JPA/ORM', '쿼리 튜닝'],
  '클라우드':  ['AWS', 'GCP', 'Azure', 'Docker', 'Kubernetes', 'Terraform', 'CI/CD', 'Nginx', 'Linux', '모니터링'],
};
// 플랫 목록 (검증·백엔드 전송용 합집합)
export const INTEREST_OPTIONS = Object.values(INTEREST_GROUPS).flat();

// AI 모델 티어 — 상위 플랜일수록 위·아래 모델을 전부 쓸 수 있다 (plans.allowed_models 확장 규칙)
// tier: 1=FREE부터, 2=PRO부터, 3=MAX부터. 이 tier 값이 그대로 크레딧 차감 가중치이기도 하다(CreditUsageServiceImpl 참고)
export const MODEL_TIERS = [
  { name: 'claude-haiku-4-5',      label: 'Claude Haiku 4.5',      tier: 1, desc: '빠른 기본 리뷰' },
  { name: 'gpt-5.6-luna',          label: 'GPT-5.6 Luna',          tier: 1, desc: '가볍고 빠름' },
  { name: 'gemini-3.5-flash', label: 'Gemini 3.5 Flash', tier: 1, desc: '가격 대비 성능' },
  { name: 'claude-sonnet-5',       label: 'Claude Sonnet 5',       tier: 2, desc: '속도·깊이 균형' },
  { name: 'gpt-5.6-terra',         label: 'GPT-5.6 Terra',         tier: 2, desc: '보안 취약점 분석 강점' },
  { name: 'claude-opus-4-8',       label: 'Claude Opus 4.8',       tier: 3, desc: '가장 깊은 크로스파일 분석' },
  { name: 'gpt-5.6-sol',           label: 'GPT-5.6 Sol',           tier: 3, desc: '최상위 보안 분석' },
];
export const PLAN_TIER = { FREE: 1, PRO: 2, MAX: 3 };

// 퀴즈 문제 유형 (FR-64 문제 유형 다양화)
export const QUIZ_TYPES = [
  ['MULTIPLE_CHOICE', '객관식'],
  ['OX', 'OX 퀴즈'],
  ['SHORT_ANSWER', '주관식'],
  ['FILL_BLANK', '빈칸 채우기'],
];

// ── 이슈 카테고리 5종 / 심각도 3단계 (요구사항 6-3 분류체계) ──
export const CATEGORIES = ['BUG', 'PERFORMANCE', 'CODE_SMELL', 'CONVENTION', 'SECURITY'];
export const SEVERITIES = ['CRITICAL', 'MAJOR', 'MINOR'];

// 화면 표기용 한글 라벨 — 코드값은 명세 그대로, 보여줄 때만 번역
export const CATEGORY_KO = { BUG: '버그', PERFORMANCE: '성능', CODE_SMELL: '코드 냄새', CONVENTION: '컨벤션', SECURITY: '보안' };
export const SEVERITY_KO = { CRITICAL: '심각', MAJOR: '주의', MINOR: '경미' };
export const ISSUE_STATUS_KO = { OPEN: '미해결', PENDING: '승인 대기', RESOLVED: '해결됨', IGNORED: '무시됨' };
export const PR_STATUS_KO = { REVIEWED: '검토 완료', REVIEWING: '검토 중', OPEN: '검토 대기', FAILED: '검토 실패' };
// 리뷰 히스토리(REVIEW_TARGET_TYPE) 표기용 — PR은 웹훅 경로, 나머지는 리뷰 스튜디오에서 만든 리뷰
export const REVIEW_TARGET_KO = { PASTE: '붙여넣기', UPLOAD: '파일 업로드', PR: 'PR', GUEST: '체험 리뷰' };
export const REVIEW_STATUS_KO = { PENDING: '처리 중', COMPLETED: '완료', FAILED: '실패' };
export const catKo = (v) => CATEGORY_KO[v] ?? v;
export const sevKo = (v) => SEVERITY_KO[v] ?? v;
