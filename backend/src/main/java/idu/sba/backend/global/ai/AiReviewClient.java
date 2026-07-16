package idu.sba.backend.global.ai;

// AI 모델 호출 계약 — domain/guest(비로그인 체험)와 domain/review(로그인 리뷰 파이프라인)가 공유해서 쓴다.
// systemPrompt는 호출부가 이미 완성해서 넘긴다(도메인마다 프롬프트 내용은 다르지만, 호출 방식은 공통).
// 4xx/5xx·JSON 파싱 실패는 BusinessException(ErrorCode.AI_MODEL_CALL_FAILED)로 변환해서 던지는 게 계약이다.
public interface AiReviewClient {

    AiReviewResult review(AiModel model, String systemPrompt, String code, String language);

}
