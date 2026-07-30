/*
 * 랜딩(데스크톱·모바일)이 같이 쓰는 요금제 표시용 값.
 *
 * 이름·가격·크레딧·모델은 서버(/api/plans)가 원천이다. 여기엔 화면 문구와,
 * 서버가 안 뜰 때 쓸 폴백만 둔다. 두 랜딩에 문구를 따로 두면 한쪽만 고쳐져 어긋난다.
 *
 * 문구는 실제 동작 기준이다 — 플랜이 가르는 건 고를 수 있는 모델과 하루 크레딧뿐이고,
 * 리뷰·학습카드의 깊이는 거기 딸려 온다(prompts/learning/model_tier_1~3.txt).
 * 옛 문구("보안 분석 PRO부터", "크로스 파일 분석 MAX", "팀 초대 MAX", "AWS Bedrock 기반")는
 * 코드에 근거가 없었다 — 팀 초대는 repo 도메인에 플랜 검사가 아예 없어 FREE도 된다.
 */

// 줄바꿈(\n)은 문장 경계다. 데스크톱 랜딩은 white-space: pre-line으로 그대로 끊고,
// 폰은 폭이 좁아 어차피 다시 접히므로 공백으로 이어 붙는다
export const PLAN_NOTE: Record<string, string> = {
  FREE: "가벼운 모델로 짧고 확실한 것만.\n애매하면 확인이 필요하다고 적어 줍니다.",
  PRO: "흔한 예제 대신 실제 사용 될 수 있는 예제로,\n퀴즈 오답에 대한 피드백까지 채웁니다.",
  MAX: "약점을 맞닿은 다른 개념까지 이어서 설명합니다.",
};

// 플랜이 가르지 않는 것 — 두 랜딩의 꼬리말에서 같이 쓴다
export const PLAN_FOOTNOTE =
  "* 플랜은 고를 수 있는 모델과 하루 크레딧을 정합니다. 팀 초대·주간 리포트 같은 기능은 FREE에서도 그대로 씁니다.";

// 서버가 못 뜰 때 보여줄 값. plans 테이블 시드와 같다
export const PLAN_FALLBACK = [
  { name: "FREE", price: 0, dailyCreditLimit: 20,
    allowedModels: "claude-haiku-4-5,gpt-5.6-luna,gemini-3.5-flash" },
  { name: "PRO", price: 30000, dailyCreditLimit: 40,
    allowedModels: "claude-haiku-4-5,gpt-5.6-luna,gemini-3.5-flash,claude-sonnet-5,gpt-5.6-terra" },
  { name: "MAX", price: 50000, dailyCreditLimit: 70,
    allowedModels: "claude-haiku-4-5,gpt-5.6-luna,gemini-3.5-flash,claude-sonnet-5,gpt-5.6-terra,claude-opus-4-8,gpt-5.6-sol" },
];

// "gpt-5.6-luna" → "GPT Luna". Plan.tsx에도 같은 함수가 있지만 그쪽은 남의 파일이라 건드리지 않는다
export const modelNames = (csv?: string) =>
  (csv ?? "").split(",").filter(Boolean).map((id) =>
    id.split("-")
      .filter((seg) => !/^\d/.test(seg)) // 버전 세그먼트(4, 5.6, 3.5 …) 제거
      .map((seg) => (seg === "gpt" ? "GPT" : seg.charAt(0).toUpperCase() + seg.slice(1)))
      .join(" "));

// 상위 플랜에서 새로 열리는 모델만. 전부 나열하면 세 카드가 같은 목록이 된다
export const addedModels = (plans: { allowedModels?: string }[], i: number) =>
  modelNames(plans[i]?.allowedModels)
    .filter((m) => !modelNames(plans[i - 1]?.allowedModels).includes(m));
