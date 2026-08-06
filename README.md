# COGI · Code Guide 

> AI가 GitHub Pull Request를 리뷰하고, 발견한 약점을 학습 카드와 주간 성장 리포트로 이어주는 **성장형 코드 리뷰 플랫폼**

🔗 **배포:** http://43.202.36.123 · 💻 **Repository:** https://github.com/Leeuswa/COGI

---

##  프로젝트 개요

주니어 개발자는 코드 리뷰를 받을 기회가 적고, 받더라도 *일회성 피드백*에 그쳐 실력 향상으로 이어지지 않습니다. **COGI**는 이 문제를 **`리뷰 → 약점 분석 → 학습 → 재점검`** 루프로 자동화합니다. GitHub PR·코드 붙여넣기·파일 업로드 등 어떤 코드든 다중 LLM으로 리뷰해 이슈를 심각도·카테고리로 정리하고, 그 결과를 개인 약점 통계·학습 콘텐츠·주간 리포트로 연결해 "리뷰가 성장으로 남도록" 설계했습니다.

- **팀:** Team Fable (4인)
- **컨셉:** 단발성 리뷰가 아닌, 리뷰–학습–성장의 연속 루프

---

##  핵심 기능

###  AI 코드 리뷰
- **다중 LLM 지원** — Claude · GPT · Gemini · Groq를 공통 인터페이스로 추상화, 모델 티어·폴백 전략 적용
- **입력 방식** — GitHub PR 연동 / 코드 붙여넣기 / 파일 업로드 / 비로그인 체험 리뷰(24시간 3회)
- **이슈 분류** — 심각도 3단계(심각·주의·경미) × 카테고리 5종(버그·성능·코드 냄새·컨벤션·보안)
- **이슈 판정 흐름** — 해결/무시/재검증(reverify), CRITICAL 이슈는 팀장 승인 필요
- **리포트 내보내기** — Markdown / PDF(한글 폰트 임베드)
- `javaparser` 기반 코드 정적 파싱으로 리뷰 정확도 보강

###  인증 · 보안
- **로그인** — 이메일 + 소셜(GitHub · Kakao) 통합 인증
- **이메일 인증** — 6자리 코드, 5분 만료 · 60초 재발송 쿨다운 · 시도 초과 잠금
- **JWT(HttpOnly 쿠키)** — XSS 토큰 탈취 차단, 세션 만료 시 자동 로그아웃
- **2단계 인증(TOTP)** — 설정 → 코드 검증 → 활성, 재설정 차단, 로그인 후 상태 유지
- **로그인 5회 실패 시 계정 잠금** + 비밀번호 재설정으로만 해제
- **비밀번호 재설정** — 인증코드 → 재설정 토큰(만료·재사용 방지) → 기존과 동일 비밀번호 차단
- **회원 탈퇴** — 개인정보 익명화(소프트 삭제), 팀장 탈퇴 시 최선임 팀원에게 권한 자동 위임
- 계정 상태 관리(ACTIVE / SUSPENDED / WITHDRAWN), 온보딩·약관 재동의 게이트

###  팀 · GitHub 연동
- GitHub OAuth 연동, 레포 연결, PR 목록·파일 조회
- **Webhook** — 서명 검증, 미지원 이벤트 무시, 재시도
- 팀원 초대(GitHub 아이디·이메일), 수락/거절, 권한(OWNER/MEMBER), 팀장 위임, 강제 내보내기

###  학습
- 리뷰 약점 기반 **약점 통계** + **학습 카드**(신호등 등급 YELLOW→GREEN→GREEN+)
- 카드별 **퀴즈** · 제출 · 연속 학습(streak) · AI 학습 계획
- **AI 기술 추천** + 즐겨찾기, 강의 추천

### 성장 · 리텐션
- **주간 리포트** — 매주 월요일 배치 자동 생성 + 이메일 발송, 이슈 단위 드릴다운
- **성장 추이** — 주차별 이슈 발생/해결 추이, 팀장의 팀원 비교
- **리텐션 펫(코기)** — 코인·XP·청결도·연속 출석, 자정 크레딧 초기화

###  결제 · 관리자
- 토스페이먼츠 빌링키 자동결제, 플랜 전환(업/다운그레이드·해지·재개), 크레딧 사용량
- **관리자 콘솔** — AI 사용량·비용 대시보드(벤더 Admin API 연동), 회원 관리, 전체 공지(긴급/일반), 리뷰 지침·약관·FAQ·1:1 문의

---

##  기술 스택

| 구분 | 기술 |
|---|---|
| **Backend** | Java 25, Spring Boot 4.1, Spring Security 7 (OAuth2 Client), Spring Data JPA / Hibernate 7.4 |
| **인증** | JWT(`jjwt`, HttpOnly 쿠키), TOTP(`dev.samstevens.totp`), BCrypt |
| **DB / Cache** | MariaDB, Redis |
| **기타 서버** | Spring Mail(인증·리포트), Spring Scheduling(배치), `javaparser`(정적 분석) |
| **Frontend** | React, Vite, TypeScript, React Router, jsPDF, Toss Payments SDK |
| **Infra / DevOps** | Docker · Docker Compose(MariaDB·Redis·Backend·Frontend), AWS EC2, Nginx |
| **AI / External** | Anthropic Claude · OpenAI · Google Gemini · Groq, Anthropic Admin Usage/Cost API, GitHub · Kakao OAuth |

---

##  아키텍처

```
[React (Vite/TS)] ──HTTPS──> [Nginx] ──> [Spring Boot API]
                                            ├─ MariaDB  (영속 데이터 · 34개 테이블)
                                            ├─ Redis    (게스트 리뷰 제한 등)
                                            └─ 외부: LLM 벤더 · GitHub/Kakao OAuth · Toss · SMTP
```

- **도메인 주도 패키지 구조**: `auth · user · review · learning · growth · repo · pr · payment · admin · notification · retention · terms · inquiry · webhook · guest · global`
- **인증**: 액세스 토큰을 HttpOnly 쿠키로 발급해 XSS 노출을 차단, 소셜/이메일 로그인을 하나의 인증 흐름으로 통합
- **배포**: Docker Compose로 4개 컨테이너 오케스트레이션, 환경변수(`.env`)·설정을 저장소에서 분리

---

##  담당 역할 (이수환)

- **인증 / 보안** — 소셜·로컬 로그인 통합, TOTP 2단계 인증, 로그인 잠금, 비밀번호 재설정, JWT(HttpOnly 쿠키)
- **회원 탈퇴** — 개인정보 익명화(소프트 삭제) + 팀장 권한 자동 위임, 단위 테스트 작성
- **성장 추이 · 주간 리포트** — 주차별 이슈 추이 집계·시각화, 배치 기반 리포트 생성·이메일 발송, 이슈 드릴다운
- **배포** — Docker Compose 기반 AWS EC2 배포, 환경변수/타임존 분리, OAuth 콜백·HTTPS 구성

---

##  기술적으로 고민한 점 · 트러블슈팅

- **인증 예외 설계** — 정상 로그인보다 *예외 상황*(만료된 인증코드, 재사용된 재설정 토큰, 기존과 동일한 비밀번호, 활성 상태 미유지 등)을 촘촘히 막는 것이 핵심이었습니다. "되게 만드는 것"보다 "안 되는 경우를 막는 것"이 보안 설계임을 체감했습니다.
- **주간 리포트 미생성 버그** — 대상 주 계산에 남아 있던 테스트용 코드가 항상 빈 주를 집계하게 만든 원인을 추적해 수정했습니다. 증상이 아닌 근본 원인까지 좁혀 들어가는 디버깅을 경험했습니다.
- **배포 환경 차이** — 로컬과 다른 환경변수·OAuth redirect URI·타임존 문제를 직접 부딪히며, 코드 완성과 "실제로 동작하는 환경 구축"은 다른 일임을 배웠습니다.
- **AI 사용량·비용 정합성(팀 공통)** — 벤더 비용 API가 워크스페이스 단위로만 집계되고 당일 데이터가 지연되는 제약을 파악해, 과거는 벤더 실측·오늘은 자체 로그(실시간) 하이브리드로 표시하고 조회 캐싱으로 API rate limit을 회피했습니다.

---

##  로컬 실행

```bash
# backend  (DB/Redis/메일/LLM 키는 .env · application.properties로 주입)
cd backend && ./gradlew bootRun

# frontend
cd frontend && npm install && npm run dev
```

