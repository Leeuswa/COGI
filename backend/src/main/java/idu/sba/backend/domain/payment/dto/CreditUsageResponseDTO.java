package idu.sba.backend.domain.payment.dto;

// GET /api/users/me/credit-usage 응답용
public record CreditUsageResponseDTO(
        int usedCredits,  // 오늘 사용한 크레딧
        int dailyLimit,   // 오늘의 일일 한도(요금제 기준)
        int remaining     // 남은 크레딧(dailyLimit - usedCredits, 0 미만 방지)
) {
    public static CreditUsageResponseDTO of(int usedCredits, int dailyLimit) {
        return new CreditUsageResponseDTO(usedCredits, dailyLimit, Math.max(0, dailyLimit - usedCredits));
    }
}
