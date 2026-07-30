# COGI 작업 인계

새 채팅에서 이 폴더를 열면 **이 문서부터 읽는다.** 저장소엔 안 올린다(본인용).

기준 시점: `develop` = `6131995` (PR #97 머지 직후, 2026-07-29).

**⚠️ 작업 33개 파일이 커밋 안 된 채 남아 있다.** 8절 아래 "아직 커밋 안 한 것"을 먼저 봐라.

---

## 0. 새 채팅 시작하면 이 두 줄부터

```
/ponytail lite
/caveman lite
```

- **ponytail** — 코드는 최소한만. YAGNI, 기존 패턴 재사용, 새 추상화 금지. **이 저장소의 코드 규칙이다**
- **caveman lite** — 답변에서 군더더기만 뺀다. 문장은 온전하게. 기술 용어·코드·에러 문자열은 그대로

레벨: `lite` `full` `ultra`. 끄기: `stop ponytail` / `stop caveman`.

안 켜면 코드가 과설계되고 답변이 길어진다. **매 세션 처음에 켠다.**

### 그 밖의 작업 규칙

- **협력자 트레일러 금지.** 커밋에 `Co-Authored-By: Claude` 넣지 않는다. 전부 정상연 단독 커밋
- **커밋·푸시는 시킬 때만.** 알아서 올리지 않는다
- **프론트와 백엔드는 PR을 나눈다.** 한 브랜치엔 한 영역만
- 주석은 **왜 그렇게 했는지** 위주로. 무엇을 하는지는 코드가 말한다
- 남의 코드는 고치기 전에 `git blame` 하고 물어본다

---

## 1. 스킬 — 이 폴더에 파일로 심어 뒀다

```
.claude/skills/   27개
.claude/agents/   12개
```

계정이 바뀌어도 **이 폴더에서 열면 그대로 뜬다.** 설치 명령 필요 없다. 저장소엔 안 올렸으니 폴더를 옮기면 같이 옮겨야 한다.

| 스킬 | 쓸 때 |
|---|---|
| `/ponytail lite` | 항상 (코드 규칙) |
| `/caveman lite` | 항상 (답변 규칙) |
| `/caveman-commit` | 커밋 메시지 |
| `/caveman-review` | 코드 리뷰 한 줄 코멘트 |
| `/ponytail-audit` `/ponytail-debt` | 과설계 진단 |
| `/caveman-help` `/ponytail-help` | 치트시트 |
| `/anthropic-skills:humanize` | 한글 글 "AI 티" 제거 (빠름) |
| `/anthropic-skills:humanize-korean` | 같은 일, 5인 파이프라인 정밀 모드 |
| `/anthropic-skills:xlsx` | 엑셀 만들기·읽기 |

humanize는 `.claude/skills/humanize-korean/references/`(분류표·플레이북·metrics.py)를 읽는다. **그 폴더 지우면 정확도가 떨어진다.**

---

## 2. AI 프롬프트는 `.txt`다 — `.md` 아니다

`LearningPromptBuilder`가 `ClassPathResource`로 읽는다. 22개 전부 develop에 있다.

```
backend/src/main/resources/prompts/
├── _common_rules.txt
├── prompt_groq_language.txt
├── prompt_level_{beginner,intermediate,advanced}.txt
├── prompt_plan_{free,pro,max}.txt
└── learning/
    ├── card_common.txt
    ├── card_level_{beginner,intermediate,advanced}.txt
    ├── quiz_common.txt
    ├── quiz_level_{beginner,intermediate,advanced}.txt
    ├── model_tier_{1,2,3}.txt          모델 등급별 지시
    ├── skill_recommend.txt              자유 입력 추천 (JSON 출력)
    ├── skill_recommend_by_weakness.txt
    └── study_plan.txt
```

**조립 방식** — 카드·퀴즈는 `공통 + 수준(초/중/고) + 모델 티어(1~3)` 세 조각을 이어 붙인다. 같은 카테고리라도 초급/고급, 저가/고가 모델에서 결과가 달라진다.

프롬프트를 고치면 **실제로 한 번 돌려서 출력을 봐야 한다.** JSON 스키마를 바꿨는데 파싱이 깨지면 `@Transactional` 롤백으로 크레딧은 돌아오고, 서버 로그에 AI 원문 800자가 남는다.

---

## 3. 첫 세션 세팅

```bash
cd frontend && npm install
cd backend && ./gradlew build -x test
```

`backend/src/main/resources/application.properties`는 **gitignore라 로컬에만 있다.** 없으면 백엔드가 안 뜬다. 팀에서 받아라. 현재 `ddl-auto=update`.

DB 새로 심기 (시드는 DB가 비었을 때만 돈다):

```bash
mariadb -u root -p12345678 -e "DROP DATABASE cogi; CREATE DATABASE cogi CHARACTER SET utf8mb4;"
```

시드 계정 — 전부 비번 `1234`.

| 계정 | 역할 | 비고 |
|---|---|---|
| `admin@a.a` | 팀장 | GitHub 미연동 |
| `user@a.a` | 팀원 | **GitHub 미연동** (연동 흐름을 이 계정으로 밟는다) |
| `kim@a.a` `lee@a.a` | 팀원 | 연동됨 |

---

## 4. 구조

```
frontend/  React + Vite + TS, 포트 5173 (:8080 프록시)
backend/   Spring Boot + JPA + MariaDB, 포트 8080
```

`develop`이 본류. 작업은 `feature/*`로 파고 PR로 올린다.

---

## 5. 내가 맡은 영역

| 영역 | 범위 |
|---|---|
| **learning 도메인** | 약점 통계·학습카드·퀴즈·학습계획·강의추천·AI 스킬 추천 (`LearningServiceImpl` 659줄 중 633줄) |
| **retention 도메인** | streak, 다마고치(`pet_states`) |
| **repo 파일 조회** | `GithubFileClient`(56줄 전부), `RepoFileController` — 미리보기 자산 |
| **프롬프트 12개** | `prompts/learning/*` |
| **시드** | `DataInitializer` |
| **모바일 전체** | `pages/mobile/*`, `styles/mobile/*` |
| **미리보기 도크** | `PreviewDock.tsx` |

### 남의 코드 — 고치기 전에 물어본다

| 사람 | 영역 |
|---|---|
| 홍성찬 | review·pr·repo 도메인, `AiModel.java`, `GlobalExceptionHandler` |
| 유지한 | 강의추천(`getCourseRecommendations`), `Studio.tsx`의 `selectRepo`, `Plan.tsx`의 `fmtModels`, 팀 페이지 |
| Leeeuswa | `app.css` 558~561, `GlobalExceptionHandler` |
| `ynkite` = 정상연 | 같은 사람. 자유롭게 수정 |

`AiModel.java`·`AiReviewClient.java`는 홍성찬님과 절반씩 섞여 있다. 손대지 마라.

---

## 6. 모바일 — 화면 14개

데스크톱은 **한 줄도 안 건드린다.** 폰(≤640px)은 별도 컴포넌트를 그린다.

### 분기는 래퍼가 한다 (중요)

한 컴포넌트 안에서 조건부 `return`으로 가르면 **그 위의 `useEffect`가 폰에서도 그대로 돈다.** 같은 API를 두 번 때려서 실제로 화면이 깨졌다.

```tsx
export default function Weakness() {
  return useIsMobile() ? <MobileWeakness /> : <DesktopWeakness />;
}

function DesktopWeakness() { /* 기존 본체 그대로 */ }
```

### 화면 하나에 CSS 하나

```
pages/mobile/MobileXxx.tsx  ── import ──▶  styles/mobile/xxx.css
```

공통 껍데기(`.mapp` `.mcard` `.mcard-head` `.mnote` `.mlead` `.mseg` `.msel` `.mempty`)만 `styles/mobile.css`에 있고 `main.tsx`가 로드한다.

### 앱 셸

- `components/MobileHeader.tsx` — `← 화면이름 🪙⚡`
- `components/MobileTabBar.tsx` — 하단 5탭(홈·리뷰·학습·성장·더보기). 더보기는 바텀시트
- `height:100dvh` + 본문만 스크롤. PWA(`public/manifest.webmanifest`)

### 완료 목록

대시보드 · PR 리뷰 · 리뷰 히스토리 · 팀 · 약점 통계 · 학습카드 목록/상세 · 강의 추천 · AI 스킬 · 성장 추이 · 주간 리포트 · 요금제 · 마이페이지 · 리뷰 스튜디오

**기능은 데스크톱과 같아야 한다.** 팀 화면에서 위임·내보내기·이메일 초대 폴백을 빠뜨렸다가 지적받고 복구한 적 있다. 새 화면은 원본의 `api.*` 호출과 버튼을 먼저 세고 시작한다.

전용 화면이 아직 없는 곳: `/app/repos` `/app/faq` `/app/admin`. `mobile.css` 안전망으로 버티는 중.

---

## 7. 밟은 함정 — 다시 밟지 마라

**`@Lob`에 `length`를 안 주면 `tinytext`(255B)다.**
퀴즈 `explain`과 `AiSkillRecommendation`이 **같은 함정을 두 번** 밟았다. AI 답변 몇 줄에 `Data too long` 500. 긴 문자열엔 반드시 `@Column(length = 65535)`. `ddl-auto=update`는 **기존 컬럼 타입을 안 바꾸므로** 이미 만든 DB엔 `ALTER`가 따로 필요하다.

**조회 API에서 쓰지 마라.**
`getWeaknessStats`가 GET마다 `deleteByUserId` 후 재삽입을 했다. 화면 두 곳이 같이 뜨거나 StrictMode가 두 번 돌리면 뒤 트랜잭션이 이미 지운 행을 지우려다 500. 그 표를 **읽는 코드가 어디에도 없어서** 쓰기를 걷어내고 `readOnly`로 돌렸다.

**유니크 제약 없는 컬럼은 `findFirstBy...`.**
동시 요청으로 같은 행이 두 개 생기면 `Optional` 조회가 `NonUniqueResultException`으로 터진다. 약관 재동의도 같은 이유로 `findFirst`를 쓴다.

**실패를 빈 목록으로 삼키지 마라.**
`.catch(() => setList([]))`는 "데이터 없음"과 구분이 안 된다. 같은 API인데 화면마다 증상이 달라져 원인 찾는 데 오래 걸렸다.

**한글 줄바꿈은 `word-break: keep-all`.**
`overflow-wrap: anywhere`는 "한 군데로 모/으는"처럼 어절 한가운데를 끊는다. 파일 경로·식별자에만 `anywhere`.

**폰 코드블록은 가로 스크롤 대신 줄을 접는다.**
`white-space: pre-wrap`. 옆으로 넘기면 오른쪽이 잘린 줄도 모른다.

**공통 껍데기를 화면별로 덮을 땐 `.mapp`를 붙인다.**
`.mseg`(4칸)와 `.mc-seg`(5칸)는 특이도가 같아 파일 순서가 이겼다. `.mapp .mc-seg`로 올려야 안 밀린다.

**CSS 주석에 마크다운 별표를 쓰지 마라.**
별표+빗금이 주석을 먼저 닫아 뒤 규칙이 통째로 깨진다. `app.css`가 그랬다.

**후손 선택자로 카드 테두리를 주지 마라.**
`.mrp li`가 펼친 상세 안의 목록까지 잡아 이중 테두리가 됐다. `.mrp > li`.

**컨테이너 `display`를 바꿔 세로 정렬하지 마라.**
`.app-body`를 grid로 바꿨더니 암시적 열이 콘텐츠 폭으로 줄어 화면이 좁아졌다. 블록 그대로 두고 `align-content: center`만.

**`Silkscreen`은 숫자에 조심.**
한글 글리프가 없다. `<b>`로 굵게 걸면 가짜 굵게가 먹어 `4` 같은 글자가 겹쳐 뭉갠다. 숫자는 `GalmuriMono11`.

**본인 판별은 `userId`로.**
`githubUsername`으로 비교하면 미연동끼리 둘 다 `null`이라 남을 나로 착각한다.

**`srcDoc` iframe의 기준 주소는 부모 페이지다.**
원본 HTML의 상대 링크가 우리 앱(localhost)으로 풀린다. 미리보기에서 링크 클릭을 막아야 한다. **아직 안 고침 — FEEDBACK.md 1-1.**

**클래스 이름 충돌.** `.stat`(코기 스탯바)·`.stat-card`(요금제)와 겹쳐 두 번 깨졌다. 모바일은 `m-` 접두.

**가로 스크롤 절대 금지.** 표·차트·긴 식별자가 원인.

**모바일 시트는 `z-index: 60/61`이다.**
하단 탭바가 `50`이라 그보다 낮게 주면 시트 아래쪽(닫기 버튼)이 탭바에 가린다. 계획 시트를 40/41로 줬다가 밟았다. 층은 이렇다 — GNB 40 · **탭바 50** · 더보기 시트 55/56 · 나머지 시트 60/61 · 모달 90.

**날짜 키는 `toISOString()`으로 만들지 마라.**
UTC로 밀려서 한국시간 자정~오전 9시에 **하루가 어긋난다.** 달력·주간 스트립처럼 날짜를 비교하는 곳은 로컬 기준으로 직접 만든다.
```js
const ymdKey = (d) =>
  `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,"0")}-${String(d.getDate()).padStart(2,"0")}`;
```

**눌리는 맛은 실제로 눌리는 요소에만.**
`.cal-day.hit:hover`에 `translateY(-2px)`가 걸려 있어서, 눌러도 아무 일 없는 🔥 칸이 버튼처럼 들썩였다. hover 효과와 클릭 가능 여부를 같은 클래스로 묶어라.

**알림을 조회 API에서 만들지 마라.**
"접속하면 오늘 계획 알림 생성"은 쓰기다. `GET`에 섞으면 StrictMode가 두 번 돌려 중복이 생긴다. `POST /plan-notifications/today`로 빼고 서버에서 멱등하게(같은 `link` + 오늘이면 skip) 만든다.

---

## 8. 지금까지 한 것 (전부 develop에 머지됨)

PR #80 · #81 · #88 · #89 모두 머지 완료.

**백엔드**
- 약점 통계 재집계 500 해결 (읽기-쓰기 제거)
- AI 스킬 추천 — 자유 입력도 약점 기반과 같은 JSON 스키마로 통일, `ai_skills` 행 저장으로 즐겨찾기 가능, `latest?kind=`로 두 결과 각각 복원
- 다마고치 `pet_states` 저장, streak·달력을 서버 값으로
- 학습 계획 3/5/7/14일, 수준·모델 티어별 프롬프트
- `GET /api/repos/{repoId}/file-content` — 미리보기용 원본 파일

**프론트엔드**
- 모바일 화면 14개 + 앱 셸
- 미리보기 도크 — 빠진 파일 깃허브/로컬 업로드, `<head>` 보존, 토글 버튼, 실행취소 복구, 변경 라인 스크롤 동기화
- AI 스킬 3탭 분리, 즐겨찾기 → 스튜디오 연결
- 스튜디오 후속 답변 마크다운 렌더, 채팅 전체보기

### 그 뒤 남이 머지한 것 (#90~#97)

내 작업이 아니다. 착수 전에 겹치는지 본다.

| 커밋 | 사람 | 내용 |
|---|---|---|
| `72479bc` | 이수환 | **주간 리포트 이슈 드릴다운** + 시드 보강 + PDF 겹침 수정 |
| `5a3da1d` | 홍성찬 | PR 리뷰 현황 **서버사이드 필터** |
| `c4547cb` | 유지환 | **벤더 실사용량 조회**(OpenAI/Anthropic Admin API) + 공지 페이지네이션 |

`GithubRepoLinkServiceImpl:85`에 **웹훅 자동 등록**도 들어갔다.

---

## 8-1. 아직 커밋 안 한 것 (2026-07-29 세션)

33개 파일. **프론트와 백엔드를 나눠서 두 브랜치로 올려야 한다.**

**백엔드 13** (신규: `PlanDateResponseDTO.java`)
- 누적 학습일 `totalDays` — `streak`은 "연속"이라 하루 걸리면 0이다. 라벨만 총 학습일이었고 값이 틀렸다
- **학습 계획 등록** — `learning_cards.plan_started_at` 한 칸. 각 단계 날짜 = 등록일 + `dayOffset`이라 일정 테이블을 안 만들었다
  - `POST /{cardId}/study-plan/register` · `GET /plan-dates?from&to` · `POST /plan-notifications/today`
  - 계획을 다시 만들면 `planStartedAt`을 null로 — 옛 등록일 + 새 offset은 엉뚱한 날짜다
- `NotificationService.createOncePerDay` — 같은 `link`로 오늘 만든 게 있으면 skip
- `study_plan.txt` — 1단계 `dayOffset` 0 강제 + **기간의 모든 날을 채운다**(빈 날은 5분 복습 단계)

**프론트 20** (신규: `MobileBell.tsx`)
- 미리보기 링크 클릭 차단(FEEDBACK 1-1) · `isFrontend` 확장자 확대(1-2)
- 총 학습일(누적) ↔ 이번 달 학습일 분리, 성장 화면 포함 4곳
- 약점 타일 2×2 고정 — 개수와 무관하게 패널 높이가 안 변한다
- Silkscreen 굵기 400 — Bold는 `4`의 뚫린 칸이 메워져 깨져 보인다
- **AI 스킬 즐겨찾기 탭** — 백엔드는 이미 있던 `/api/ai-skills/favorites`를 쓴다
- 계획 등록 UI — `.ics` **한 파일**, 달력 점, 날짜 클릭 팝업, `?step=N` 딥링크·강조
- 모바일 주간 스트립 주 넘김(±12주, 조회 구간과 같은 상수)
- **모바일 헤더 알림 종** — 시트로. 배지 키는 데스크톱과 공유

**미검증 — 서버를 안 띄웠다.**
- `plan_started_at` 컬럼 생성(`ddl-auto=update`)
- 프롬프트 두 건의 실제 출력 (1단계가 오늘부터인지, 빈 날 없이 채워지는지, 복습 단계 `focus`가 빈말이 아닌지)

---

## 9. 다음에 할 일

**[FEEDBACK.md](FEEDBACK.md)를 보면 된다.** 다만 아래 두 항목은 **FEEDBACK.md가 틀렸다.**

- **크레딧 알림** — "90%만 뜬다"고 적혀 있는데 `GameContext.tsx:128`에 100% 소진 + 자정 초기화 안내가 **최초 커밋부터 있었다.** 할 일이 아니다
- **MD 내보내기** — "형식이 안 산다"고 적혀 있는데 `PrDetail.tsx:102`에 이미 뷰어 무관 정본으로 다시 짜여 있다. 노션에 붙여넣어 보고 판단할 일이지 코드 작업이 아니다

### 남은 것

| 항목 | 담당 | 성격 |
|---|---|---|
| 프롬프트 출력 검수 (모델등급×수준 12개) | 나 | 노가다. **다음 세션에서 이어서 함** |
| 초급 프롬프트 용어 풀어쓰기 | 나 | 지시문은 `card_level_beginner.txt:7`에 이미 있다. **실제 출력을 안 봤을 뿐** |
| AI 타임아웃 실발동 확인 | 나 | 검증. 연결10/응답60 설정만 있고 터뜨려 본 적 없다 |
| 온보딩 동선 (랜딩→가입→첫 리뷰) | 나 | 큼. WBS "입문 온보딩"과 같은 건 |
| **로그인 전 페이지 모바일 전용** | 나 | 착수 전. `Landing.tsx`(264줄)부터. 범위 미정 |
| 리뷰 결과 정렬·묶기 | 홍성찬 | 방식 팀 미정 |

### WBS 기준 미착수 — 입문 온보딩 전체

`404(COGI)_분석, 설계 통합본 최종.xlsx`의 WBS에 **내 이름으로 5개 항목**이 있는데 코드에 흔적이 없다.

- 기본 템플릿 5종 스켈레톤 · AI 채팅 커스터마이징 · 체험 환경(iframe·MSW·요청로그) · 입문 학습카드 · 완주 트래킹

분량이 크니 일정부터 팀에 확인해라.

### 관리자 벤더 실사용량 — 키만 넣으면 된다

유지환이 `c4547cb`로 다 만들어 뒀다. `application.properties`에 두 줄이 비어 있어서 화면이 빈 상태다.

```properties
ai.openai.adminKey=
ai.claude.adminKey=
```

리뷰용 `ai.openai.key`와 **다른 키**다. 조직 **Admin 키**(`sk-ant-admin01-...`)가 따로 필요하고, 개인 계정으로는 못 만든다 — Console → Settings → Organization에서 조직 전환 후 Settings → Admin keys.

주의: 이 API는 **API 사용량만** 준다. 개발에 쓰는 Claude 팀 구독(claude.ai) 토큰은 여기 안 잡힌다. Gemini·Groq도 전용 usage API가 없어 빠져 있어서, FREE 플랜 트래픽은 우리 `ai_usage_logs` 자체 집계로만 보인다.

---

## 10. 검증

```bash
cd frontend && npm run build
```

```bash
cd backend && ./gradlew compileJava
```

테스트 실패 9건은 `MAIL_USERNAME` 환경변수가 없어서 나는 기존 문제다. 내 변경과 무관하다.

**빌드만으로는 부족한 변경이 있다.** DB 컬럼(`ddl-auto=update`)과 프롬프트(`ClassPathResource`)는 서버를 띄워야 반영된다. 프롬프트를 고쳤으면 반드시 한 번 돌려서 출력을 본다.

```bash
cd backend && ./gradlew bootRun
```

---

## 11. 저장소에 안 올린 파일

이 폴더에만 있다. 옮길 때 같이 옮겨야 한다.

| 파일 | 내용 |
|---|---|
| `HANDOFF.md` | 이 문서 |
| `FEEDBACK.md` | 7/28 시연 피드백 + 다음 할 일 |
| `07_28 회의록_멘토링 피드백.xlsx` | 회의록 (07_17 양식) |
| `.claude/` | 스킬 27 + 에이전트 12 |

서버 코드(`backend/` `frontend/`)는 **develop과 완전히 같다.** 올릴 게 남아 있지 않다.
