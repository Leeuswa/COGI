package idu.sba.backend.domain.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true) // 토스 응답의 안 쓰는 필드 무시 (없으면 역직렬화 실패)
public record TossBillingKeyResponseDTO(
        String billingKey,
        String customerKey,
        Card card
){
    public record Card (
            String number // 마스킹된 카드번호
    ){}
}