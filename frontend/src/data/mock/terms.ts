// 약관 — Signup·MyPage 약관 탭 화면용 목 데이터
// ── terms (AUTH-009) — 가입 시 필수 2 + 선택 1, 결제 시 필수 1 ──
// PAYMENT 는 가입 화면엔 안 나오고 결제 진행 시 추가로 동의받는다 (AUTH-009 비고, PAY-001 연계)
// content 는 지금은 한 줄 요약 — 법무 검토본이 나오면 이 필드만 교체하면 된다
export const mockTerms = [
  { id: 1, type: 'SERVICE',   version: '1.0', title: '서비스 이용약관',          isRequired: true,
    content: '제1조(목적) 이 약관은 COGI 서비스의 이용 조건과 회원·회사의 권리와 의무를 정합니다.' },
  { id: 2, type: 'PRIVACY',   version: '1.0', title: '개인정보 처리방침',        isRequired: true,
    content: '회사는 서비스 제공에 필요한 최소한의 개인정보(이메일, GitHub 계정)만 수집하며 목적 달성 시 지체 없이 파기합니다.' },
  { id: 3, type: 'MARKETING', version: '1.0', title: '마케팅 수신 동의(이메일)',  isRequired: false,
    content: '신규 기능과 학습 리포트 소식을 이메일로 보내드립니다. 동의하지 않아도 서비스 이용에 제한이 없습니다.' },
  { id: 4, type: 'PAYMENT',   version: '1.0', title: '결제/구독 이용약관',       isRequired: true,
    content: '구독 요금은 결제일 기준 1개월 단위로 청구되며, 해지 시 남은 기간은 일할 계산으로 정산됩니다.' },
];

// FR-91 재동의 판정 결과 — required 를 true 로 바꾸면 앱 상단에 재동의 배너가 뜬다
export const mockReagreement = { required: false, termTitle: '서비스 이용약관', newVersion: '1.1' };
