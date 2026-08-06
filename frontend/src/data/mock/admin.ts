// 관리자 — Admin 4탭 화면용 목 데이터
// ── ai_usage_logs (ADM-001) ──
export const mockUsageLog = {
  id: 1,
  userId: 1,
  modelName: 'gemini-3-pro',
  inputTokens: 1840,
  outputTokens: 512,
  cost: 0.0031,                  // USD
  requestType: 'REVIEW',
  createdAt: '2026-07-10T02:48:11',
};

// ── review_guidelines (ADM-004, 수준별 프롬프트 지침) ──
export const mockGuidelines = {
  BEGINNER: '용어를 풀어서 설명하고, 왜 문제인지 → 어떻게 고치는지 순서로. 예제는 5줄 이내.',
  INTERMEDIATE: '트레이드오프를 함께 언급. 대안 2개까지 제시하고 성능 영향은 수치로.',
  ADVANCED: '설계 관점 코멘트 우선. 크로스 파일 영향과 아키텍처 리스크 위주로 짧게.',
};
