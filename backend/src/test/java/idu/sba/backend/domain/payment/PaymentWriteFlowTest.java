package idu.sba.backend.domain.payment;

import idu.sba.backend.domain.payment.entity.ChangeType;
import idu.sba.backend.domain.payment.entity.Subscription;
import idu.sba.backend.domain.payment.entity.SubscriptionHistory;
import idu.sba.backend.domain.payment.entity.SubscriptionStatus;
import idu.sba.backend.domain.payment.repository.PaymentMethodRepository;
import idu.sba.backend.domain.payment.repository.SubscriptionHistoryRepository;
import idu.sba.backend.domain.payment.repository.SubscriptionRepository;
import idu.sba.backend.global.security.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 로그인 상태에서 결제 write 흐름(등록→구독→변경→해지)이 실제 DB에 통과하는지 검증.
// @Transactional → 테스트 끝나면 롤백되어 DB에 흔적 안 남음.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-for-jwt-hmac-sha256-min-length-0123456789",
        "spring.mail.username=test",
        "spring.mail.password=test"
})
class PaymentWriteFlowTest {

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwtProvider;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired SubscriptionHistoryRepository historyRepository;
    @Autowired PaymentMethodRepository paymentMethodRepository;

    // DB 시드 기준: user 1 존재, plans FREE=1 / PRO=2 / MAX=3
    private static final long USER_ID = 1L;
    private static final long FREE = 1L, PRO = 2L, MAX = 3L;

    private String auth() {
        return "Bearer " + jwtProvider.createToken(USER_ID, "USER");
    }

    @Test
    void 결제_write_흐름_로그인상태() throws Exception {
        // 1) 결제수단 등록 → 201, id 반환
        String pmBody = mvc.perform(post("/api/payments/methods")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardInfo\":\"test-card\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long paymentMethodId = Long.parseLong(pmBody.trim());
        assertThat(paymentMethodRepository.findById(paymentMethodId)).isPresent();

        // 2) 구독 생성 (FREE → PRO = UPGRADE) → 201, subId 반환
        String subBody = mvc.perform(post("/api/subscriptions")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":" + PRO + ",\"paymentMethodId\":" + paymentMethodId + ",\"agrreTerms\":[]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long subId = Long.parseLong(subBody.trim());

        Subscription sub = subscriptionRepository.findById(subId).orElseThrow();
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(sub.getPlanId()).isEqualTo(PRO);
        assertThat(sub.getUserId()).isEqualTo(USER_ID);

        List<SubscriptionHistory> afterCreate = historyRepository.findBySubscriptionId(subId);
        assertThat(afterCreate).hasSize(1);
        assertThat(afterCreate.get(0).getPreviousPlanId()).isEqualTo(FREE);
        assertThat(afterCreate.get(0).getNewPlanId()).isEqualTo(PRO);
        assertThat(afterCreate.get(0).getChangeType()).isEqualTo(ChangeType.UPGRADE);
        assertThat(afterCreate.get(0).getProratedAmount()).isEqualTo(10000); // 최초구독 = 가격 그대로

        // 3) 플랜 변경 (PRO → MAX = UPGRADE) → 200, history DTO 반환
        mvc.perform(patch("/api/subscriptions/" + subId)
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPlanId\":" + MAX + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousPlanId").value((int) PRO))
                .andExpect(jsonPath("$.newPlanId").value((int) MAX))
                .andExpect(jsonPath("$.changeType").value("UPGRADE"));

        assertThat(subscriptionRepository.findById(subId).orElseThrow().getPlanId()).isEqualTo(MAX);
        assertThat(historyRepository.findBySubscriptionId(subId)).hasSize(2);

        // 4) 해지 → 200, status=CANCELLED
        mvc.perform(delete("/api/subscriptions/" + subId)
                        .header("Authorization", auth()))
                .andExpect(status().isOk());
        assertThat(subscriptionRepository.findById(subId).orElseThrow().getStatus())
                .isEqualTo(SubscriptionStatus.CANCELLED);
    }
}