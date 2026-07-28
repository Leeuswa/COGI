# COGI 작업 인계 — 모바일 전용 화면 만들기

브랜치 `feature/data` · 전부 미커밋 상태

---

## 지금 하던 일

데스크톱은 **한 줄도 건드리지 않고**, 폰(≤640px)에서만 별도 컴포넌트를 그리는 작업.
데스크톱 화면을 CSS로 접는 게 아니라 **모바일용 화면을 새로 짠다.**

### 분기 방식

각 데스크톱 페이지 맨 위에 두 줄만 넣는다.

```tsx
const isMobile = useIsMobile();          // hooks/useIsMobile.ts (640px)
if (isMobile) return <MobileXxx />;
```

### 파일 규칙 — 화면 하나에 CSS 하나

```
pages/mobile/MobileXxx.tsx   ─ import ─→  styles/mobile/xxx.css
```

CSS는 컴포넌트가 직접 `import`한다. 공통 껍데기(`.mapp` `.mcard` `.mcard-head` `.mnote` `.mlead` `.mseg` `.msel` `.mempty`)만 `styles/mobile.css`에 있고 `main.tsx`가 로드한다.

### 앱 셸

- `components/MobileHeader.tsx` — `← 화면이름 🪙⚡`. 경로로 제목을 뽑는다
- `components/MobileTabBar.tsx` — 하단 5탭(홈·리뷰·학습·성장·더보기). 더보기는 바텀시트
- 셸은 `height:100dvh` + 본문만 스크롤. PWA(`public/manifest.webmanifest`)로 홈 화면 추가 시 전체화면

---

## 완료한 모바일 화면 (14개)

| 화면 | 컴포넌트 | 핵심 변경 |
|---|---|---|
| 대시보드 | `MobileDashboard` | 오늘 할 일 → 요약칩 2×2 → 코기 → 주간 7칸 → 약점 → 성적 막대 |
| PR 리뷰 | `MobilePrList` | 7열 표 → 카드 리스트. 심각도 세그먼트 |
| 리뷰 히스토리 | `MobileHistory` | 표+팝업 → 카드 아코디언 |
| 팀 | `MobileTeam` | 3열 표 → 팀원 리스트 |
| 약점 통계 | `MobileWeakness` | 버튼을 아래로 내려 폭 100% |
| 학습카드 목록 | `MobileCards` | 2열 격자 → 리스트 + 진행 막대 |
| 강의 추천 | `MobileCourses` | 카드 전체가 링크 |
| AI 스킬 추천 | `MobileSkills` | AI 선택 2×2 |
| 학습카드 상세 | `MobileCardDetail` | **탭 4개**(개념·문제·계획·복습) |
| 성장 추이 | `MobileGrowth` | SVG 곡선 → 주별 막대. 겹쳐보기는 팀원별 카드 |
| 주간 리포트 | `MobileReports` | 한 줄 요약+팝업 → 카드 아코디언 |
| 요금제 | `MobilePlan` | 3열 카드 세로로, 이력 표 → 리스트 |
| 마이페이지 | `MobileMy` | 탭 5개 → 3×2 세그먼트, 약관 표 → 리스트 |
| 리뷰 스튜디오 | `MobileStudio` | 화면 전체가 채팅. PR 피커는 바텀시트 |

**기능은 데스크톱과 동일해야 한다.** 팀 화면에서 새 팀 만들기·위임·내보내기·나가기·이메일 초대 폴백을 빠뜨렸다가 지적받고 복구한 적 있다. 새 화면을 만들 때 원본의 `api.*` 호출과 버튼을 먼저 세고 시작할 것.

---

## 남은 모바일 화면

없음. `/app/repos`·`/app/faq`·`/app/admin`은 아직 데스크톱 화면이 그대로 뜬다 (mobile.css 안전망으로 버티는 중).

---

## 지켜야 할 규칙

- **다른 사람 코드 금지.** 수정 전 `git blame`으로 해당 줄 작성자를 확인하고, 남의 코드면 먼저 물어본다.
  - 홍성찬 — review·pr·repo 도메인, `AiModel.java`, `GlobalExceptionHandler`
  - 유지한 — 강의추천(`getCourseRecommendations`), `Studio.tsx`의 `selectRepo`
  - Leeeuswa — `app.css` 558~561, `GlobalExceptionHandler`
  - `ynkite` = 정상연(같은 이메일). 정상연 코드는 자유롭게 수정
- **가로 스크롤 절대 금지.** 표·차트·코드블록·긴 식별자가 원인. 코드블록만 자기 안에서 스크롤 허용
- **줄바꿈** — 한 줄로 쓸 수 있으면 한 줄. 안 되면 문장 끝에서 `\n`이나 `<br />`. 애매하게 접히면 안 됨
- **`Silkscreen` 폰트에는 한글 글리프가 없다.** 한글에 쓰면 흐릿하게 깨진다. 숫자·영문에만
- **클래스 이름 충돌 주의.** `.stat`(코기 스탯바), `.stat-card`(요금제)와 겹쳐 두 번 깨진 적 있다. 모바일은 `m-` 접두 사용
- **공통 껍데기를 화면별로 덮을 때는 `.mapp`를 붙인다.** `.mseg`(기본 4칸)와 `.mc-seg`(5칸)는 특이도가 같아 파일 순서가 이겼다. `.mapp .mc-seg`로 올려야 안 밀린다
- **한글 줄바꿈은 `word-break: keep-all`.** `anywhere`는 "한 군데로 모/으는"처럼 어절 한가운데를 끊는다. 파일 경로·식별자에만 `anywhere`
- **CSS 주석에 `**굵게**/` 같은 마크다운을 쓰지 말 것.** 별표+빗금이 주석을 먼저 닫아 뒤 규칙이 통째로 깨진다
- **후손 선택자로 카드 테두리를 주지 말 것.** `.mrp li`가 펼친 상세 안 목록까지 잡아 이중 테두리가 됐다. `.mrp > li`
- 코드 작성은 ponytail 기준 — 최소한만, 기존 패턴 따르기, 새 추상화 금지
- 주석은 간결하게, 왜 그렇게 했는지 위주로

---

## 이번 회차에 같이 고친 것 (모바일 외)

- **퀴즈 500 해결** — `LearningCardQuiz.explain`에 `@Column(name="explanation")`을 length 없이 붙여 `@Lob`이 `tinytext`(255B)로 매핑됐다. 해설 몇 문장이면 `Data too long`. `length=65535`로 수정. `options`·`answer`도 확장
- **DataInitializer** — `admin@a.a`/`user@a.a`/`kim@a.a`/`lee@a.a` (전부 비번 `1234`). admin은 팀장이고 GitHub 미연동
- **신규 기능** — AI 스킬 추천(`ai_skills`·`ai_skill_favorites`), 다마고치 DB 저장(`pet_states`), 학습계획 3/5/7/14일
- **카테고리 한글** — `코드 냄새` → `구조 개선`, `컨벤션` → `코드 스타일` 등 (`constants.ts`의 `CATEGORY_KO`)

## 검증

```bash
cd frontend && npm run build      # 타입체크 + 빌드
cd backend && ./gradlew test      # 실패 9건은 MAIL_USERNAME 환경변수 없어서 나는 기존 문제
```

DB를 새로 심으려면:

```bash
mariadb -u root -p12345678 -e "DROP DATABASE cogi; CREATE DATABASE cogi CHARACTER SET utf8mb4;"
```

`application.properties`는 gitignore 대상이라 로컬에만 있다. 현재 `ddl-auto=update`.
