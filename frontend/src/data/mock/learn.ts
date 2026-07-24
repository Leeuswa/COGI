// 학습 — Weakness·Cards·CardDetail·Courses·Skills 화면용 목 데이터
// ── weakness_stats (LRN-001) — 동일 카테고리 3회 이상만 집계됨 (FR-56) ──
// 두 상태를 다 보여준다: 카드가 이미 있는 약점(→ 카드로 이동) / 아직 없는 약점(→ 만들기 버튼)
export const mockWeaknessList = [
  {
    id: 1, category: 'null 안전성', categoryCode: 'BUG',
    language: 'TypeScript',      // PR 분석 시 언어 자동 판별 (FR-65)
    occurrenceCount: 3, updatedAt: '2026-07-10',
    cardId: 1,                   // 이미 학습카드 생성됨 → 목록에선 카드 링크로
  },
  {
    id: 2, category: '에러 처리 누락', categoryCode: 'BUG',
    language: 'Java',
    occurrenceCount: 4, updatedAt: '2026-07-09',
    cardId: null,                // 아직 카드 없음 → 만들기 버튼
  },
];
export const mockWeakness = mockWeaknessList[0]; // 단건 참조 호환용

// ── course recommendations (LRN-003~004) — 약점별 인프런/Udemy 검색 딥링크 ──
// 백엔드 CourseRecommendationResponseDTO 모양 그대로 (query + links는 서버가 조립)
const deeplinks = (q) => [
  { platform: 'INFLEARN', label: '인프런에서 찾기', url: `https://www.inflearn.com/ko/courses?s=${encodeURIComponent(q)}` },
  { platform: 'UDEMY', label: 'Udemy에서 찾기', url: `https://www.udemy.com/courses/search/?q=${encodeURIComponent(q)}&lang=ko` },
];
export const mockCourseRecommendations = [
  { category: 'BUG', categoryLabel: '버그', language: 'TypeScript', occurrenceCount: 3, query: 'TypeScript 버그', links: deeplinks('TypeScript 버그') },
  { category: 'BUG', categoryLabel: '버그', language: 'Java', occurrenceCount: 4, query: 'Java 버그', links: deeplinks('Java 버그') },
];

// ── learning_cards (LRN-002) — 신호등 등급 RED 상태로 시작 ──
export const mockCard = {
  id: 1,
  category: 'null 안전성',
  level: 'BEGINNER',
  language: 'TypeScript',
  grade: 'RED',                  // RED → YELLOW(정답 3) → GREEN(정답 7) (FR-61)
  correctCount: 0,
  isBookmarked: false,
  isCompleted: false,
  conceptContent:
    'null/undefined 인 객체의 속성에 접근하면 그 순간 런타임 에러로 앱이 멈춘다. ' +
    '옵셔널 체이닝(?.)은 대상이 비어 있으면 undefined 를 돌려주며 안전하게 넘어가고, ' +
    '널 병합(??)은 null·undefined 일 때만 기본값을 채운다. 0이나 빈 문자열까지 잡아버리는 || 와는 다르니 주의.',
  // 실패 사례 → 개선 코드 순서. 리뷰 #42에서 실제로 잡힌 라인 기준
  badExample:
    "// ❌ before — user가 null이면 여기서 터진다 (리뷰 #42, 88번 라인)\n" +
    "function greet(user) {\n" +
    "  return `hi, ${user.name}`;\n" +
    "}",
  goodExample:
    "// ✅ after — 비어 있으면 '손님'으로 안전하게\n" +
    "function greet(user) {\n" +
    "  return `hi, ${user?.name ?? '손님'}`;\n" +
    "}",
  // 카드 하단 3줄 요약 — 퀴즈 풀기 전 마지막 점검용
  keyPoints: [
    '?. 는 접근을 안전하게, ?? 는 기본값을 채운다 — 역할이 다르다',
    '|| 는 0, 빈 문자열, false 까지 기본값으로 바꿔버린다',
    '근본 해결은 "왜 null이 들어왔는지"를 찾는 것',
  ],
  history: [
    { date: '2026-07-10', grade: 'RED', note: '카드 생성 · 리뷰 #42 약점 승격' },
  ],
};

// ── learning_card_quizzes (LRN-002, 문제 유형 다양화 FR-64) ──
// 유형 선택 UI 테스트를 위해 유형별 1건씩. 생성 API에 questionType 을 넘기면 맞는 걸 내려준다.
export const mockQuizzes = [
  {
    id: 1, cardId: 1, questionType: 'MULTIPLE_CHOICE',
    question: 'user?.name 에서 ?. 연산자의 이름은?',
    options: ['옵셔널 체이닝', '널 병합 연산자', '전개 연산자', '논리 OR'],
    answer: '옵셔널 체이닝',
  },
  {
    id: 2, cardId: 1, questionType: 'OX',
    question: '`a ?? b` 는 a가 빈 문자열("")일 때도 b를 반환한다.',
    options: ['O', 'X'],
    answer: 'X',
    explain: '?? 는 null/undefined 만 잡는다. 빈 문자열까지 바꾸는 건 || 쪽.',
  },
  {
    id: 3, cardId: 1, questionType: 'SHORT_ANSWER',
    question: 'null·undefined 일 때만 기본값을 채워주는 연산자를 기호로 쓰면? (예: &&)',
    answer: '??',
  },
  {
    id: 4, cardId: 1, questionType: 'FILL_BLANK',
    question: "return `hi, ${user___.name ?? '손님'}`;  — 밑줄에 들어갈 기호는?",
    answer: '?.',
  },
];
