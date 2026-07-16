package idu.sba.backend.domain.review.client;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class AiReviewResult {

    private final List<AiReviewIssueDto> issues;
    private final int inputTokens;
    private final int outputTokens;
    private final double cost;

}
