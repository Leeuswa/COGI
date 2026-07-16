package idu.sba.backend.global.ai;

// AI가 찾은 이슈 한 건. domain/guest(ReviewComment)·domain/review(ReviewIssue 엔티티) 양쪽이
// 이 공용 타입에서 자기 도메인 타입으로 매핑해서 쓴다.
public record AiReviewIssue(
        String category,      // BUG / PERFORMANCE / CODE_SMELL / CONVENTION / SECURITY
        String severity,      // CRITICAL / MAJOR / MINOR
        String filePath,       // nullable
        Integer lineNumber,    // nullable
        String description
) {}
