package idu.sba.backend.domain.learning.dto;

import lombok.Getter;

// 퀴즈 생성 요청 (API-045) — 프론트가 { questionType }을 보낸다
@Getter
public class QuizCreateRequestDTO {
    private String questionType; // MULTIPLE_CHOICE/OX/SHORT_ANSWER/FILL_BLANK
}
