package idu.sba.backend.domain.payment.service;

import idu.sba.backend.domain.payment.entity.CreditUsage;
import idu.sba.backend.domain.payment.entity.Plan;
import idu.sba.backend.domain.payment.repository.CreditUsageRepository;
import idu.sba.backend.domain.payment.repository.PlanRepository;
import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreditUsageServiceImplTest {

    @Mock private CreditUsageRepository creditUsageRepository;
    @Mock private SubscriptionService subscriptionService;
    @Mock private PlanRepository planRepository;

    @InjectMocks
    private CreditUsageServiceImpl service;

    private static final Long USER_ID = 1L;

    private Plan plan(String name, String allowedModels) {
        Plan plan = new Plan();
        setField(plan, "name", name);
        setField(plan, "allowedModels", allowedModels);
        return plan;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    // 세 요금제 모두 스텁해두지만, resolveModelWeight는 FREE→PRO→MAX 순으로 먼저 걸리면 그 뒤는 안 봄
    // (등급이 낮은 모델을 테스트할 땐 PRO/MAX 스텁이 실제로는 안 쓰임) → lenient로 미사용 스텁 경고를 피함
    private void stubPlans() {
        lenient().when(planRepository.findByName("FREE")).thenReturn(Optional.of(
                plan("FREE", "claude-haiku-4-5,gpt-5.6-luna,gemini-3.5-flash")));
        lenient().when(planRepository.findByName("PRO")).thenReturn(Optional.of(
                plan("PRO", "claude-haiku-4-5,gpt-5.6-luna,gemini-3.5-flash,claude-sonnet-5,gpt-5.6-terra")));
        lenient().when(planRepository.findByName("MAX")).thenReturn(Optional.of(
                plan("MAX", "claude-haiku-4-5,gpt-5.6-luna,gemini-3.5-flash,claude-sonnet-5,gpt-5.6-terra,claude-opus-4-8,gpt-5.6-sol")));
    }

    private CreditUsage usageStartingAt(int usedCredits) {
        CreditUsage usage = new CreditUsage();
        usage.start(USER_ID, LocalDate.now(), 20);
        for (int i = 0; i < usedCredits; i++) {
            usage.consume(1);
        }
        return usage;
    }

    @Test
    void FREE_등급_모델은_1_소모() {
        stubPlans();
        CreditUsage usage = usageStartingAt(0);
        when(creditUsageRepository.findByUserIdAndUsageDate(eq(USER_ID), any())).thenReturn(Optional.of(usage));

        service.checkAndConsume(USER_ID, "claude-haiku-4-5");

        assertThat(usage.getUsedCredits()).isEqualTo(1);
    }

    @Test
    void PRO_등급_모델은_2_소모() {
        stubPlans();
        CreditUsage usage = usageStartingAt(0);
        when(creditUsageRepository.findByUserIdAndUsageDate(eq(USER_ID), any())).thenReturn(Optional.of(usage));

        service.checkAndConsume(USER_ID, "claude-sonnet-5");

        assertThat(usage.getUsedCredits()).isEqualTo(2);
    }

    @Test
    void MAX_등급_모델은_3_소모() {
        stubPlans();
        CreditUsage usage = usageStartingAt(0);
        when(creditUsageRepository.findByUserIdAndUsageDate(eq(USER_ID), any())).thenReturn(Optional.of(usage));

        service.checkAndConsume(USER_ID, "claude-opus-4-8");

        assertThat(usage.getUsedCredits()).isEqualTo(3);
    }

    @Test
    void 가중치를_더하면_한도를_넘는_경우_예외() {
        stubPlans();
        CreditUsage usage = usageStartingAt(19); // FREE 한도 20 중 19 사용
        when(creditUsageRepository.findByUserIdAndUsageDate(eq(USER_ID), any())).thenReturn(Optional.of(usage));

        // MAX 등급 모델(가중치 3)을 쓰면 19+3=22 > 20 이라 초과
        assertThatThrownBy(() -> service.checkAndConsume(USER_ID, "claude-opus-4-8"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CREDIT_LIMIT_EXCEEDED);

        assertThat(usage.getUsedCredits()).isEqualTo(19); // 실패 시 소모되지 않아야 함
    }

    @Test
    void 오늘_사용_이력이_있으면_그대로_조회된다() {
        CreditUsage usage = usageStartingAt(5);
        setField(usage, "dailyLimit", 20);
        when(creditUsageRepository.findByUserIdAndUsageDate(eq(USER_ID), any())).thenReturn(Optional.of(usage));

        var status = service.getStatus(USER_ID);

        assertThat(status.usedCredits()).isEqualTo(5);
        assertThat(status.dailyLimit()).isEqualTo(20);
        assertThat(status.remaining()).isEqualTo(15);
    }

    @Test
    void 오늘_사용_이력이_없으면_플랜_한도로_0_사용_반환() {
        when(creditUsageRepository.findByUserIdAndUsageDate(eq(USER_ID), any())).thenReturn(Optional.empty());
        Plan freePlan = plan("FREE", "claude-haiku-4-5");
        setField(freePlan, "dailyCreditLimit", 20);
        when(subscriptionService.getCurrentPlanEntity(USER_ID)).thenReturn(freePlan);

        var status = service.getStatus(USER_ID);

        assertThat(status.usedCredits()).isEqualTo(0);
        assertThat(status.dailyLimit()).isEqualTo(20);
        assertThat(status.remaining()).isEqualTo(20);
    }

}
