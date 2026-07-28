package idu.sba.backend.domain.review.dto;

import lombok.Getter;

//리뷰 후속 질문 [설계 추론] 응답 — 프론트(Studio.tsx)는 answer만 그대로 말풍선에 띄운다
@Getter
public class AskQuestionResponseDTO {

    private final String answer;

    private AskQuestionResponseDTO(String answer) {
        this.answer = answer;
    }

    public static AskQuestionResponseDTO of(String answer) {
        return new AskQuestionResponseDTO(answer);
    }

}
