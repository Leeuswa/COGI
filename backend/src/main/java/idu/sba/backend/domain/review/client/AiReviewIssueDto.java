package idu.sba.backend.domain.review.client;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AiReviewIssueDto {

    // _common_rules.txt의 출력 스키마 그대로: BUG/PERFORMANCE/CODE_SMELL/CONVENTION/SECURITY
    private final String category;
    // CRITICAL/MAJOR/MINOR
    private final String severity;
    private final String filePath; //nullable
    private final Integer lineNumber; //nullable
    private final String description;

}
