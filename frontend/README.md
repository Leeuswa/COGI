# COGI Frontend — 다마고치 아케이드

Team 404 · KDT 해커톤 8회차. AI 코드리뷰 × 약점 학습 × 코기 다마고치.

## 실행

```bash
npm install
npm run dev     # http://localhost:5173
npm run build   # 배포 빌드 (dist/)
```

## 목 모드 → 백엔드(Spring Boot) 전환

1. `src/api/client.js` 맨 위 `USE_MOCK = true` → `false`
2. `vite.config.js` 의 proxy 주석 해제 (`/api`, `/oauth2` → :8080)
3. 끝. 화면 코드는 수정할 게 없음 (전 페이지가 client.js만 바라봄)

백엔드 완성 후 정리 절차 (3단계):
① `src/api/client.js` 상단 `USE_MOCK = false`  ← 이것만으로 전 기능이 실서버 호출로 전환됨
② `src/data/mock/` 폴더 삭제
③ client.js 상단의 `import * as M from '../data/mock'` 한 줄 삭제
mock 필드명은 테이블 정의서 컬럼명의 camelCase — DTO를 이 이름으로 맞추면 프론트 무수정.
(`src/data/constants.js` 는 기능정의서의 enum이라 삭제하지 않는다)

## 목 모드 치트시트

- 테스트 계정 4종 (비밀번호 전부 `1234`)
  - `team` 팀장 — 팀 페이지 OWNER 뷰 (링크 공유·수락·내보내기)
  - `member` — GitHub 초대 도착 상태 (종 알림 → 팀 페이지 → 즉시 수락 합류 시연)
  - `user` 일반 — GitHub 미연동 + 이메일 초대 도착 (알림 → 연동 → 팀 합류 시연)
  - `admin` 관리자 — 관리자 콘솔 + 팀원(MEMBER) 뷰 시연
- 팀 초대 링크: `/invite/FABLE-2026` — 비로그인으로 열면 로그인/가입 유도 흐름 확인 가능
- 비밀번호 재설정(마이페이지): 현재 비밀번호는 `1234` 일 때만 통과 (목 규칙)
- 이메일 인증코드 / OTP 코드: `000000`
- 카카오/GitHub 회원가입: 버튼 누르면 `mock/user.js` 의 목 프로필(닉네임 `코기집사` 등)이 자동으로 붙음.
  소셜 가입자는 마이페이지에서 이름·이메일 수정이 잠김(소셜 계정 소유 정보).
  이메일 가입자는 닉네임만 수정 가능, 이메일은 로그인 ID라 잠김.
- 관리자 메뉴 보기: `src/data/mock/user.js` 의 `mockUser.role` 을 `'ADMIN'` 으로
- 온보딩 다시 보기: localStorage 의 `cogi-user` 삭제 후 재로그인
- 게임/streak/크레딧 초기화: localStorage 의 `cogi-game` 삭제
- 게스트 체험 횟수 초기화: localStorage 의 `cogi-guest-used` 삭제
- 받은 초대함 테스트: 레포 페이지에 목 초대 1건(`mockInvitations`)이 떠 있음 — 수락/거절(API-030/030-1)
- 게스트 리뷰 → 계정 매핑(API-039-1): 게스트 체험 후 로그인/가입하면 signIn에서 자동 클레임

## 구조

```
src/
  api/client.js        API-001~064 함수화. USE_MOCK 스위치. 캘린더 연동(구글/애플/카카오) 포함
  data/mock/           하드코딩 전용 폴더 — 화면 영역별 7파일 + index (엔티티당 1건). 백엔드 붙으면 폴더째 삭제
  data/constants.js    도메인 enum (관심기술/카테고리/심각도) — 백엔드 이후에도 유지
  context/
    AuthContext.jsx    JWT + 유저 (localStorage). 로그인 시 게스트 리뷰 자동 클레임
    GameContext.jsx    코기 스탯·코인 / streak(제출 기준) / 일일 크레딧(20/40/70) / 서버 동기화
  components/
    CorgiDevice.jsx    다마고치 본체 (원본 HTML 게임 로직 그대로 이식)
    ui.jsx             Nav·Footer·Reveal·Tabs·칩·신호등 등 공용
  assets/sprites.js    코기 도트 스프라이트 16종 (base64)
  styles/              base(토큰·리셋·네비) / landing(랜딩·다마고치) / app(앱 화면) / responsive — 4분할
  pages/               랜딩 / 게스트 / 인증 / 온보딩 / 대시보드 / 리뷰 / 학습 / 성장 / 요금제 / 마이 / 관리자
    admin/tabs/        관리자 탭당 1파일 (Usage/Members/Logs/Guides) — 컨테이너는 상태·핸들러만
    my/tabs/           마이페이지 탭당 1파일 (Profile/Github/Security/Terms)
```

## 스펙 근거

- 기능정의서 FR-01~91, API 명세서 API-001~064, 테이블 정의서 → `404COGI_분석_설계_통합본_3.xlsx`
- 각 파일/함수 주석에 해당 FR·API 번호를 달아둠 (추적 용이)
