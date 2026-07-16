package idu.sba.backend.domain.payment.client;

import idu.sba.backend.domain.payment.dto.PaymentMethodRequestDTO;
import idu.sba.backend.domain.payment.dto.TossBillingKeyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class TossPaymentClient {

    private final RestClient tossRestClient;  // secretKey, authHeader() 다 필요 없어짐

    public TossBillingKeyResponse issueBillingKey(String customerKey, PaymentMethodRequestDTO dto) {
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
                .body(TossBillingKeyResponse.class);
    }

}