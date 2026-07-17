package idu.sba.backend.domain.payment.dto;


// POST /api/payments/methods 요청용
public record PaymentMethodRequestDTO(
        String cardNumber,             // 카드번호
        String cardExpirationYear,     // 유효기간 년(YY)
        String cardExpirationMonth,    // 유효기간 월(MM)
        String cardPassword,           // 카드 비밀번호 앞 2자리
        String customerIdentityNumber  // 생년월일 6자리 또는 사업자번호
) {}
