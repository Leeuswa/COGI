package idu.sba.backend.domain.payment.dto;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPaymentApproveResponseDTO(
        String paymentKey, String orderId, String status, Integer totalAmount
) {}
