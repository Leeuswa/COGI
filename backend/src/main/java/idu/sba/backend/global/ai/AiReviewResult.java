package idu.sba.backend.global.ai;

import java.util.List;

public record AiReviewResult(
        String summary,           // nullable — 게스트 체험 화면에서만 사용, 로그인 리뷰 파이프라인은 안 씀
        List<AiReviewIssue> issues,
        int inputTokens,
        int outputTokens,
        double cost,                // 이 호출의 실제 비용(달러) — AiModel의 단가로 계산
        boolean analyzable          // false면 입력이 코드가 아니거나 분석 불가 — 로그인 파이프라인은 이 경우 크레딧을 환불한다
) {}
