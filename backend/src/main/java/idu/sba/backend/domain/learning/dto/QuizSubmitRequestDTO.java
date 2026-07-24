package idu.sba.backend.domain.learning.dto;

import lombok.Getter;

// 퀴즈 제출 요청 (API-046) — 프론트가 { answer }만 보낸다
@Getter
public class QuizSubmitRequestDTO {
    private String answer;
}
