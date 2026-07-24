package idu.sba.backend.domain.admin.dto;

import idu.sba.backend.domain.review.entity.AiUsageLog;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminAiUsageResponseDTO(
        Long id, //PK
        LocalDateTime createdAt, // 리뷰 시각

        Long userId, //null이면 비로그인
        String nickname, //표시용 유저 이름 (null이면 비로그인/삭제된 유저)
        String modelName, // 어떤 모델
        String requestType, // REVIEW , LEARNING_CARD , SKILL 등 추후 ENUM 고려

        Integer inputTokens,  //입력 토큰 수
        Integer outputTokens, //출력 토큰 수
        BigDecimal cost //비용 (정확한 계산을 위해 Double대신 BigDecimal 사용)
){

    // of 는 엔티티 -> DTO 변환을 한곳에 모은 편의 메서드 생성
    public static AdminAiUsageResponseDTO of(AiUsageLog l, String nickname) {
        return new AdminAiUsageResponseDTO(
                l.getId(), l.getCreatedAt(), l.getUserId(), nickname, l.getModelName(),
                l.getRequestType(), l.getInputTokens(), l.getOutputTokens(), l.getCost());
    }
}
