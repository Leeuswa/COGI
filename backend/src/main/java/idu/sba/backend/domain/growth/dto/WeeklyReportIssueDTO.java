package idu.sba.backend.domain.growth.dto;

// 주간 리포트 드릴다운 — 그 주 이슈 한 건 (프론트에서 하나하나 카드로 나열)
public record WeeklyReportIssueDTO(
        Long issueId,
        String repoName,
        Integer prNumber,
        Long prId,
        String severity,   // CRITICAL/MAJOR/MINOR
        String category,   // BUG/PERFORMANCE/...
        String filePath,
        Integer lineNumber,
        String description,
        String status      // OPEN/RESOLVED/...
) {
    // 네이티브 쿼리 Object[] → DTO. line_number 등 null 가능
    public static WeeklyReportIssueDTO from(Object[] o) {
        return new WeeklyReportIssueDTO(
                ((Number) o[0]).longValue(),
                (String) o[1],
                o[2] == null ? null : ((Number) o[2]).intValue(),
                ((Number) o[3]).longValue(),
                (String) o[4],
                (String) o[5],
                (String) o[6],
                o[7] == null ? null : ((Number) o[7]).intValue(),
                (String) o[8],
                (String) o[9]);
    }
}
