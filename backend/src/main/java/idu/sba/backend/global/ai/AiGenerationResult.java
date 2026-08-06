package idu.sba.backend.global.ai;

// 범용 생성 호출 1건의 결과 — review()와 달리 응답을 특정 스키마로 파싱하지 않고
// 원문(코드펜스 제거)만 돌려준다. 파싱은 호출 도메인이 알아서 한다(학습카드 등).
public record AiGenerationResult(String content, int inputTokens, int outputTokens, double cost) {}
