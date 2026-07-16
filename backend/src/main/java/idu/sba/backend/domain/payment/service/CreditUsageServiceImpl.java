package idu.sba.backend.domain.payment.service;

import idu.sba.backend.domain.payment.dto.CreditUsageResponseDTO;
import idu.sba.backend.domain.payment.entity.CreditUsage;
import idu.sba.backend.domain.payment.entity.Plan;
import idu.sba.backend.domain.payment.repository.CreditUsageRepository;
import idu.sba.backend.domain.payment.repository.PlanRepository;
import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CreditUsageServiceImpl implements CreditUsageService {

    // 임시 가중치 — 구독 요금제가 아니라 "실제로 호출된 모델이 처음 허용되는 요금제" 기준으로 매긴다
    // (prompt_plan_pro/max.txt의 "실제 호출된 모델 등급 기준" 보정 원칙과 동일한 논리).
    private static final int FREE_MODEL_WEIGHT = 1;
    private static final int PRO_MODEL_WEIGHT = 2;
    private static final int MAX_MODEL_WEIGHT = 3;

    // 자정 초기화 기준 시간대. 서버가 UTC라도 한국 자정에 맞추려고 명시(GuestReviewService와 동일한 이유)
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final CreditUsageRepository creditUsageRepository;
    private final SubscriptionService subscriptionService;
    private final PlanRepository planRepository;

    @Override
    @Transactional
    public void checkAndConsume(Long userId, String modelName) {
        LocalDate today = LocalDate.now(KST);
        int weight = resolveModelWeight(modelName);

        CreditUsage usage = creditUsageRepository.findByUserIdAndUsageDate(userId, today)
                .orElseGet(() -> {
                    Plan plan = subscriptionService.getCurrentPlanEntity(userId);
                    CreditUsage created = new CreditUsage();
                    created.start(userId, today, plan.getDailyCreditLimit());
                    return creditUsageRepository.save(created);
                });

        if (!usage.hasRemaining(weight)) {
            throw new BusinessException(ErrorCode.CREDIT_LIMIT_EXCEEDED);
        }
        usage.consume(weight);
    }

    @Override
    public CreditUsageResponseDTO getStatus(Long userId) {
        LocalDate today = LocalDate.now(KST);
        return creditUsageRepository.findByUserIdAndUsageDate(userId, today)
                .map(usage -> CreditUsageResponseDTO.of(usage.getUsedCredits(), usage.getDailyLimit()))
                .orElseGet(() -> {
                    Plan plan = subscriptionService.getCurrentPlanEntity(userId);
                    return CreditUsageResponseDTO.of(0, plan.getDailyCreditLimit());
                });
    }

    // FREE 목록에 있으면 1, 아니면 PRO 목록(FREE 포함 누적)에 있으면 2, 아니면 MAX 목록에 있으면 3
    private int resolveModelWeight(String modelName) {
        if (planAllowsModel("FREE", modelName)) {
            return FREE_MODEL_WEIGHT;
        }
        if (planAllowsModel("PRO", modelName)) {
            return PRO_MODEL_WEIGHT;
        }
        if (planAllowsModel("MAX", modelName)) {
            return MAX_MODEL_WEIGHT;
        }
        throw new BusinessException(ErrorCode.MODEL_NOT_ALLOWED_FOR_PLAN);
    }

    private boolean planAllowsModel(String planName, String modelName) {
        return planRepository.findByName(planName)
                .map(Plan::getAllowedModels)
                .map(allowedModels -> Arrays.stream(allowedModels.split(",")).map(String::trim).toList())
                .orElse(List.of())
                .contains(modelName);
    }

}
