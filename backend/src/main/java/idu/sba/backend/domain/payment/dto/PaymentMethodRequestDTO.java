package idu.sba.backend.domain.payment.dto;


// POST /api/payments/methods 요청용
public record PaymentMethodRequestDTO(
        String cardInfo // 테스트용 카드 정보. 이 값으로 PG(토스 등)에 빌링키 발급 요청함
) {
}
