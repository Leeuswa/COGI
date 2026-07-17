package idu.sba.backend.domain.payment.controller;

import idu.sba.backend.domain.payment.dto.*;
import idu.sba.backend.domain.payment.service.CreditUsageService;
import idu.sba.backend.domain.payment.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final CreditUsageService creditUsageService;

    //전체 요금제 목록 (인증 불필요)
    @GetMapping("/plans")
    public ResponseEntity<List<PlanResponseDTO>> getPlans() {
        return ResponseEntity.ok(subscriptionService.getPlans());
    }

    //내 현재 플랜 조회
    @GetMapping("/users/me/plan")
    public ResponseEntity<MyPlanResponseDTO> getMyPlan(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(subscriptionService.getMyPlan(userId));
    }

    //API-055: 오늘 크레딧 사용량/한도 조회
    @GetMapping("/users/me/credit-usage")
    public ResponseEntity<CreditUsageResponseDTO> getCreditUsage(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(creditUsageService.getStatus(userId));
    }

    //결제수단 등록
    @PostMapping("/payments/methods")
    public ResponseEntity<Long> registerPaymentMethod(
            @AuthenticationPrincipal Long userId,
            @RequestBody PaymentMethodRequestDTO dto
    ) {
        Long id = subscriptionService.registerPaymentMethod(userId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    //구독 시작
    @PostMapping("/subscriptions")
    public ResponseEntity<Long> createSubscription(
            @AuthenticationPrincipal Long userId,
            @RequestBody SubscriptionCreateDTO dto
    ) {
        Long subId = subscriptionService.createSubscription(userId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(subId);
    }

    //플랜 변경 (본인 구독만)
    @PatchMapping("/subscriptions/{subId}")
    public ResponseEntity<SubscriptionHistoryResponseDTO> changeSubscription(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long subId,
            @RequestBody SubscriptionChangeDTO dto
    ) {
        return ResponseEntity.ok(subscriptionService.changeSubscription(userId, subId, dto));
    }

    //구독 해지 (본인 구독만)
    @DeleteMapping("/subscriptions/{subId}")
    public ResponseEntity<Void> cancelSubscription(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long subId
    ) {
        subscriptionService.cancelSubscription(userId, subId);
        return ResponseEntity.ok().build();
    }
}