package idu.sba.backend.domain.learning.dto;

import lombok.Getter;

// 퀴즈 생성 요청 (API-045) — 프론트가 { questionType, modelName }을 보낸다
@Getter
public class QuizCreateRequestDTO {
    private String questionType; // MULTIPLE_CHOICE/OX/SHORT_ANSWER/FILL_BLANK
    private String modelName; // 리뷰 스튜디오처럼 사용자가 고른 모델. 안 보내면 카드 만들 때 쓴 모델로 간다
}
