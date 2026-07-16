package idu.sba.backend.domain.review.client;

// AI 모델 호출 계약 — 실제 HTTP 구현체(Claude/OpenAI/Gemini/Groq)는 AI 연동 담당자 몫.
// GithubApiClient와 동일하게 4xx/5xx는 이 인터페이스 밖으로 흘리지 않고
// BusinessException(ErrorCode.AI_MODEL_CALL_FAILED)로 변환해서 던지는 걸 계약으로 한다.
public interface AiReviewClient {

    AiReviewResult review(AiReviewRequest request);

}
