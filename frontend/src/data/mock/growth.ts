// 성장 추이 — Growth 화면용 목 데이터
// ── growth_trends (GRW-001) — 주 단위 이슈 빈도 1세트 ──
export const mockGrowth = [
  { label: '6/15', issueCount: 7, resolved: 2 },
  { label: '6/22', issueCount: 6, resolved: 3 },
  { label: '6/29', issueCount: 4, resolved: 3 },
  { label: '7/6',  issueCount: 2, resolved: 2 },
];

// ── weekly_reports (RET-002 변경분) ──
// [설계 변경] 원래 명세는 '매주 월요일 10시 자동 메일'(FR-75)이었으나,
// 사이트 내 주간리포트 탭에서 조회하고 원할 때만 메일 발송하는 방식으로 바꿈.
// 백엔드: 배치는 리포트 '생성'까지만 하고, 메일 발송은 사용자 요청 API로 분리 필요.
// 리스트 화면 확인용이라 예외적으로 4건.
export const mockWeeklyReports = [
  {
    id: 4, periodStart: '2026-07-06', periodEnd: '2026-07-12',
    issueCount: 2, resolvedCount: 2, prevIssueCount: 4,      // 전주 대비 계산용
    topCategory: 'NULL_SAFETY',
    categories: [                                            // 카테고리별 발생 분포 (미니 바)
      { name: 'NULL_SAFETY', count: 1 },
      { name: 'CONVENTION', count: 1 },
    ],
    quizSubmits: 5, correctRate: 80, streakEnd: 4,           // 학습 활동 요약
    summary: '반복 실수가 2건까지 줄었어요. 널 체크 습관이 자리잡는 중!',
    actions: [                                               // 다음 주 추천
      'NULL_SAFETY 카드 재도전으로 해결+ 배지에 도전해보세요',
      '컨벤션 이슈가 새로 등장 — 팀 린트 규칙을 한번 훑어보면 좋아요',
    ],
  },
  {
    id: 3, periodStart: '2026-06-29', periodEnd: '2026-07-05',
    issueCount: 4, resolvedCount: 3, prevIssueCount: 6,
    topCategory: 'NULL_SAFETY',
    categories: [
      { name: 'NULL_SAFETY', count: 2 },
      { name: 'ERROR_HANDLING', count: 1 },
      { name: 'PERFORMANCE', count: 1 },
    ],
    quizSubmits: 4, correctRate: 75, streakEnd: 3,
    summary: '지난주보다 이슈가 33% 감소. 해결률도 75%로 상승.',
    actions: ['NULL_SAFETY 퀴즈를 이어서 — 정답 3회까지 1회 남았어요'],
  },
  {
    id: 2, periodStart: '2026-06-22', periodEnd: '2026-06-28',
    issueCount: 6, resolvedCount: 3, prevIssueCount: 7,
    topCategory: 'ERROR_HANDLING',
    categories: [
      { name: 'ERROR_HANDLING', count: 3 },
      { name: 'NULL_SAFETY', count: 2 },
      { name: 'SECURITY', count: 1 },
    ],
    quizSubmits: 3, correctRate: 67, streakEnd: 2,
    summary: '에러 처리 카테고리가 새로 상위에 올라왔어요. 학습카드 생성을 추천.',
    actions: ['ERROR_HANDLING 학습카드를 만들어 개념부터 잡아보세요', '보안 이슈 1건은 팀장 승인 대기 중 — 확인 필요'],
  },
  {
    id: 1, periodStart: '2026-06-15', periodEnd: '2026-06-21',
    issueCount: 7, resolvedCount: 2, prevIssueCount: null,   // 첫 주라 비교 없음
    topCategory: 'NULL_SAFETY',
    categories: [
      { name: 'NULL_SAFETY', count: 4 },
      { name: 'CONVENTION', count: 2 },
      { name: 'PERFORMANCE', count: 1 },
    ],
    quizSubmits: 1, correctRate: 100, streakEnd: 1,
    summary: '첫 리포트! 널 안전성 이슈가 가장 잦았습니다.',
    actions: ['NULL_SAFETY 학습카드가 자동 생성됐어요 — 첫 퀴즈부터 시작', '레포에 팀원을 초대하면 팀 단위 추이도 볼 수 있어요'],
  },
];
