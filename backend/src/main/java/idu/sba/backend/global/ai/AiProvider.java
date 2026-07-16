package idu.sba.backend.global.ai;

// API 벤더(호출 방식) 단위. 모델 자체는 AiModel에서 관리.
public enum AiProvider {

    GEMINI("https://generativelanguage.googleapis.com/v1beta/openai", false),
    GROQ  ("https://api.groq.com/openai/v1",                          false),
    OPENAI("https://api.openai.com/v1",                               false),
    CLAUDE("https://api.anthropic.com",                               true);

    private final String baseUrl;
    private final boolean anthropicStyle; // true면 /v1/messages 형식, 아니면 OpenAI 호환

    AiProvider(String baseUrl, boolean anthropicStyle) {
        this.baseUrl = baseUrl;
        this.anthropicStyle = anthropicStyle;
    }

    public String baseUrl()        { return baseUrl; }
    public boolean anthropicStyle(){ return anthropicStyle; }
}
