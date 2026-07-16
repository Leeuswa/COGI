// 요금제/구독 — Plan·Landing 요금 섹션 화면용 목 데이터
// ── plans (SUB-001, FR-84~85) ──
// 모델 범위는 테이블 정의서 plans.allowed_models 그대로:
// FREE = gemini-3-pro,grok-4 / PRO = claude-sonnet-5 / MAX = claude-opus-4-8 (크레딧 20/40/70)
export const mockPlans = [
  {
    id: 1, name: 'FREE', price: 0, dailyCreditLimit: 20,
    model: 'Gemini 3 Pro · Grok 4', allowedModels: 'gemini-3-pro,grok-4',
    features: ['기본 카테고리 리뷰(버그·컨벤션)', '개념 설명 학습카드', '로컬 체험 모드'],
  },
  {
    id: 2, name: 'PRO', price: 9900, dailyCreditLimit: 40,
    model: 'Claude Sonnet 5', allowedModels: 'claude-sonnet-5',
    features: ['5종 카테고리 + 보안 취약점 분석', '예제 코드 포함 학습카드', '주간 성장 리포트(월 10시)'],
  },
  {
    id: 3, name: 'MAX', price: 19900, dailyCreditLimit: 70,
    model: 'Claude Opus 4.8', allowedModels: 'claude-opus-4-8',
    features: ['크로스 파일 심층 분석', '예제 + 심화 퀴즈', '팀 초대 · 함께 성장'],
  },
];

// ── subscription_history (PAY-002) ──
export const mockSubHistory = {
  id: 1,
  previousPlan: 'FREE',
  newPlan: 'FREE',
  changeType: 'START',
  proratedAmount: 0,             // 테스트용 계산값. 실결제 아님 (FR-88)
  changedAt: '2026-07-01T10:00:00',
};
