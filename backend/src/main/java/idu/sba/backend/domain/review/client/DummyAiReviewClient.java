package idu.sba.backend.domain.review.client;

import org.springframework.stereotype.Component;

import java.util.List;

// 임시 구현체 — 실제 AI API 연동이 붙기 전까지 파이프라인 전체(프롬프트 조합 → 저장 → 크레딧 차감)를
// 끝까지 테스트할 수 있도록 고정 응답을 반환한다. 실제 구현체(@Component)가 추가되면 이 클래스는 삭제할 것
// (같은 인터페이스에 구현체가 둘이면 스프링이 빈을 못 정함).
@Component
public class DummyAiReviewClient implements AiReviewClient {

    @Override
    public AiReviewResult review(AiReviewRequest request) {
        List<AiReviewIssueDto> issues = List.of(
                new AiReviewIssueDto("BUG", "CRITICAL", null, 1,
                        "더미 응답입니다. (AI 연동 전 임시 결과 / model=" + request.getModelName() + ")")
        );
        return new AiReviewResult(issues, 0, 0, 0.0);
    }

}
