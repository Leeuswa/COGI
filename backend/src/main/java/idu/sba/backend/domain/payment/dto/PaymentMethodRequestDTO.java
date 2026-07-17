package idu.sba.backend.domain.payment.dto;


// POST /api/payments/methods 요청용 (토스 SDK 결제창 방식)
// 프론트가 requestBillingAuth 성공 후 받은 authKey와, 그때 넘겼던 customerKey를 그대로 전달한다.
public record PaymentMethodRequestDTO(
        String authKey,      // 토스 결제창 인증 결과로 받은 키 (이걸로 빌링키 발급)
        String customerKey   // requestBillingAuth에 넘겼던 값과 반드시 동일
) {}
