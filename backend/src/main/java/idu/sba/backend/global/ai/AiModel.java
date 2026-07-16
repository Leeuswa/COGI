package idu.sba.backend.global.ai;

import java.util.Arrays;

// 실제 호출 대상 모델. 플랜별 허용 모델은 Plan.allowedModels(누적형)에서 관리하고,
// 여기서는 모델 문자열이 어느 provider로 호출할지만 매핑
public enum AiModel {

    // 비로그인 체험
    GEMINI_FLASH("gemini-3.5-flash",          AiProvider.GEMINI),
    GROQ_LLAMA_70B    ("llama-3.3-70b-versatile",  AiProvider.GROQ),

    // FREE
    CLAUDE_HAIKU("claude-haiku-4-5", AiProvider.CLAUDE),
    GPT_LUNA    ("gpt-5.6-luna",     AiProvider.OPENAI),

    // PRO
    CLAUDE_SONNET("claude-sonnet-5", AiProvider.CLAUDE),
    GPT_TERRA    ("gpt-5.6-terra",   AiProvider.OPENAI),

    // MAX
    CLAUDE_OPUS("claude-opus-4-8", AiProvider.CLAUDE),
    GPT_SOL    ("gpt-5.6-sol",     AiProvider.OPENAI);

    private final String id;
    private final AiProvider provider;

    AiModel(String id, AiProvider provider) {
        this.id = id;
        this.provider = provider;
    }

    public String id()             { return id; }
    public AiProvider provider()   { return provider; }

    // Plan.allowedModels 등 DB/요청에서 온 모델 문자열로 enum 찾기
    public static AiModel fromId(String id) {
        return Arrays.stream(values())
                .filter(m -> m.id.equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 모델입니다: " + id));
    }
}
