package idu.sba.backend.domain.payment.service;

import idu.sba.backend.domain.payment.dto.CreditUsageResponseDTO;

// 일일 리뷰 요청 크레딧 체크/차감 로직
public interface CreditUsageService {

    // 오늘자 사용량을 확인하고, 실제로 호출된 modelName의 등급(FREE=1/PRO=2/MAX=3, 구독 요금제가 아니라
    // 그 모델이 "처음 허용되는" 요금제 기준)만큼 소모. 한도 초과면 CREDIT_LIMIT_EXCEEDED 예외
    void checkAndConsume(Long userId, String modelName);

    // checkAndConsume의 반대 — AI가 analyzable=false(코드가 아니거나 분석 불가)로 응답했을 때
    // 이미 소모한 크레딧을 되돌린다. 오늘자 사용 기록이 없으면(이미 자정이 지났거나 한도초과로
    // 애초에 소모가 안 된 경우) 조용히 무시한다.
    void refund(Long userId, String modelName);

    // API-055: 오늘 사용량/한도 조회(사용 이력이 아직 없으면 0/플랜의 daily_credit_limit로 응답)
    CreditUsageResponseDTO getStatus(Long userId);

}
