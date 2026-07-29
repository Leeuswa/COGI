package idu.sba.backend.domain.growth.dto;

// 주간 리포트 드릴다운 — 지난주 이슈가 걸린 PR 한 건 (프론트에서 클릭 시 PrDetail로 이동)
public record WeeklyReportPrDTO(
        Long prId,
        String repoName,
        Integer prNumber,
        String title,
        int issueCount,     // 그 주 발생 수
        int resolvedCount   // 그중 해결 수
) {
    // 네이티브 쿼리 Object[] → DTO. COUNT/SUM은 드라이버마다 Long/BigInteger/BigDecimal이라 Number로 받아 변환
    public static WeeklyReportPrDTO from(Object[] o) {
        return new WeeklyReportPrDTO(
                ((Number) o[0]).longValue(),
                (String) o[1],
                ((Number) o[2]).intValue(),
                (String) o[3],
                ((Number) o[4]).intValue(),
                o[5] == null ? 0 : ((Number) o[5]).intValue());
    }
}
