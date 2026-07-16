package idu.sba.backend.domain.review.client;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AiReviewRequest {

    private final String modelName;
    private final String systemPrompt; //PromptBuilder가 조합한 최종 프롬프트(공통+수준+요금제)
    private final String code;
    private final String language; //nullable
    private final String inputType; //"pasted_code" 고정 — PR 경로가 붙으면 "pr_diff"도 쓰게 됨

}
