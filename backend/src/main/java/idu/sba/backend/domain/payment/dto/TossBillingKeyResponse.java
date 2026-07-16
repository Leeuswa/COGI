package idu.sba.backend.domain.payment.dto;

public record TossBillingKeyResponse (
        String billingKey,
        String customerKey,
        Card card
){
    public record Card (
            String number // 마스킹된 카드번호
    ){}
}