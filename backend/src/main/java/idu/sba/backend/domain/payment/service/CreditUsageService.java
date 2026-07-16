package idu.sba.backend.domain.payment.service;

// 일일 리뷰 요청 크레딧 체크/차감 로직
public interface CreditUsageService {

    // 오늘자 사용량을 확인하고, 실제로 호출된 modelName의 등급(FREE=1/PRO=2/MAX=3, 구독 요금제가 아니라
    // 그 모델이 "처음 허용되는" 요금제 기준)만큼 소모. 한도 초과면 CREDIT_LIMIT_EXCEEDED 예외
    void checkAndConsume(Long userId, String modelName);

}
