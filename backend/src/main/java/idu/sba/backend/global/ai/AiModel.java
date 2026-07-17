package idu.sba.backend.global.ai;

import java.util.Arrays;

// 실제 호출 대상 모델. 플랜별 허용 모델은 Plan.allowedModels(누적형)에서 관리하고,
// 여기서는 모델 문자열이 어느 provider로 호출할지, 단가가 얼마인지만 매핑
public enum AiModel {

    // 비로그인 체험
    GEMINI_FLASH_LITE("gemini-3.5-flash",     AiProvider.GEMINI, 1.50, 9.0), // 2026-07-17: 존재하지 않는 "flash-lite" 가격(0.25/1.50)이 실수로 들어가 있었음 — 실제 gemini-3.5-flash 가격으로 수정
    GROQ_LLAMA_70B    ("llama-3.3-70b-versatile",  AiProvider.GROQ,   0.0,  0.0),  // 가격 미확인 — 확인 후 갱신 필요

    // FREE
    CLAUDE_HAIKU("claude-haiku-4-5", AiProvider.CLAUDE, 1.0, 5.0),
    GPT_LUNA    ("gpt-5.6-luna",     AiProvider.OPENAI, 1.0, 6.0),

    // PRO
    CLAUDE_SONNET("claude-sonnet-5", AiProvider.CLAUDE, 2.0, 10.0), // 프로모션가, 2026-08-31까지
    GPT_TERRA    ("gpt-5.6-terra",   AiProvider.OPENAI, 2.50, 15.0),

    // MAX
    CLAUDE_OPUS("claude-opus-4-8", AiProvider.CLAUDE, 5.0, 25.0),
    GPT_SOL    ("gpt-5.6-sol",     AiProvider.OPENAI, 5.0, 30.0);

    private final String id;
    private final AiProvider provider;
    private final double inputPricePerMillion;   // $ / 100만 input 토큰
    private final double outputPricePerMillion;  // $ / 100만 output 토큰

    AiModel(String id, AiProvider provider, double inputPricePerMillion, double outputPricePerMillion) {
        this.id = id;
        this.provider = provider;
        this.inputPricePerMillion = inputPricePerMillion;
        this.outputPricePerMillion = outputPricePerMillion;
    }

    public String id()             { return id; }
    public AiProvider provider()   { return provider; }

    // ai_usage_logs.cost 계산용
    public double calculateCost(int inputTokens, int outputTokens) {
        return (inputTokens / 1_000_000.0) * inputPricePerMillion
                + (outputTokens / 1_000_000.0) * outputPricePerMillion;
    }

    // Plan.allowedModels 등 DB/요청에서 온 모델 문자열로 enum 찾기
    public static AiModel fromId(String id) {
        return Arrays.stream(values())
                .filter(m -> m.id.equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 모델입니다: " + id));
    }
}
