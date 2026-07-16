package idu.sba.backend.domain.payment.client;

import idu.sba.backend.domain.payment.dto.PaymentMethodRequestDTO;
import idu.sba.backend.domain.payment.dto.TossBillingKeyResponseDTO;
import idu.sba.backend.domain.payment.dto.TossPaymentApproveResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class TossPaymentClient {

    private final RestClient tossRestClient;  // secretKey, authHeader() 다 필요 없어짐

    public TossBillingKeyResponseDTO issueBillingKey(String customerKey, PaymentMethodRequestDTO dto) {
        Map<String, Object> body = Map.of(
                "customerKey", customerKey,
                "cardNumber", dto.cardNumber(),
                "cardExpirationYear", dto.cardExpirationYear(),
                "cardExpirationMonth", dto.cardExpirationMonth(),
                "cardPassword", dto.cardPassword(),
                "customerIdentityNumber", dto.customerIdentityNumber()
        );

        return tossRestClient.post()
                .uri("/v1/billing/authorizations/card")
                .contentType(MediaType.APPLICATION_JSON)   // Authorization 헤더 줄 삭제
                .body(body)
                .retrieve()
                .body(TossBillingKeyResponseDTO.class);
    }


    // 빌링키 → 실제 청구 (매달 스케줄러)
    public TossPaymentApproveResponseDTO confirmBilling(
            String billingKey, String customerKey, int amount, String orderId, String orderName) {
        return tossRestClient.post()
                .uri("/v1/billing/{key}", billingKey)   // path variable
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "customerKey", customerKey,
                        "amount", amount,
                        "orderId", orderId,
                        "orderName", orderName))
                .retrieve()
                .body(TossPaymentApproveResponseDTO.class);
    }

}