package idu.sba.backend.global.ai;

import idu.sba.backend.domain.guest.dto.ReviewComment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class AiReviewClient {

    private final ObjectMapper objectMapper;
    private final Map<AiProvider, String> apiKeys = new EnumMap<>(AiProvider.class);
    private final Map<AiProvider, RestClient> clients = new EnumMap<>(AiProvider.class);

    public AiReviewClient(ObjectMapper objectMapper,
                          @Value("${ai.gemini.key:}") String geminiKey,
                          @Value("${ai.groq.key:}")   String groqKey,
                          @Value("${ai.openai.key:}") String openaiKey,
                          @Value("${ai.claude.key:}") String claudeKey) {
        this.objectMapper = objectMapper;
        apiKeys.put(AiProvider.GEMINI, geminiKey);
        apiKeys.put(AiProvider.GROQ,   groqKey);
        apiKeys.put(AiProvider.OPENAI, openaiKey);
        apiKeys.put(AiProvider.CLAUDE, claudeKey);
        for (AiProvider p : AiProvider.values()) {
            clients.put(p, RestClient.builder().baseUrl(p.baseUrl()).build());
        }
    }

//    코드 리뷰 요청. 어떤 모델을 쓸지는 호출부(플랜/체험 로직)에서 AiModel로 결정해서 넘긴다.
    public AiReview review(AiModel model, String code, String language, String level) {
        String prompt = buildPrompt(code, language, level);
        AiProvider provider = model.provider();
        String json = provider.anthropicStyle()
                ? callAnthropic(provider, model.id(), prompt)
                : callOpenAiCompatible(provider, model.id(), prompt);
        return objectMapper.readValue(stripCodeFence(json), AiReview.class);
    }

    // 일부 모델이 JSON을 ```json ... ``` 코드블럭으로 감싸서 응답하는 경우 방어
    private String stripCodeFence(String raw) {
        String trimmed = raw.strip();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\s*", "");
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed.strip();
    }

    // OpenAI 호환 (Gemini/Groq/OpenAI)
    private String callOpenAiCompatible(AiProvider p, String modelId, String prompt) {
        // OpenAI 최신 모델은 max_tokens를 거부하고 max_completion_tokens를 요구함. 다른 provider는 max_tokens 그대로 사용.
        String maxTokensKey = (p == AiProvider.OPENAI) ? "max_completion_tokens" : "max_tokens";
        Map<String, Object> body = Map.of(
                "model", modelId,
                maxTokensKey, 2048,
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "response_format", Map.of("type", "json_object")
        );
        JsonNode res = clients.get(p).post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKeys.get(p))
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        return res.path("choices").get(0).path("message").path("content").asString();
    }

    // Claude
    private String callAnthropic(AiProvider p, String modelId, String prompt) {
        Map<String, Object> body = Map.of(
                "model", modelId,
                "max_tokens", 2048,
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );
        JsonNode res = clients.get(p).post()
                .uri("/v1/messages")
                .header("x-api-key", apiKeys.get(p))
                .header("anthropic-version", "2023-06-01")
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        return res.path("content").get(0).path("text").asString();
    }

    // 공통 프롬프트
    private String buildPrompt(String code, String language, String level) {
        return """
                당신은 %s 수준 개발자를 위한 코드 리뷰어입니다.
                아래 %s 코드를 리뷰하고, 다른 텍스트 없이 반드시 다음 JSON 형식으로만 답하세요:
                {"summary":"전체 요약 한두 문장",
                 "comments":[{"category":"BUG|PERFORMANCE|CODE_SMELL|CONVENTION|SECURITY",
                              "severity":"CRITICAL|MAJOR|MINOR",
                              "filePath":"파일명(모르면 input)",
                              "lineNumber":1,
                              "description":"문제와 개선 방법"}]}

                코드:
                %s
                """.formatted(level, language, code);
    }

    //파싱용 record
    public record AiReview(String summary, List<CommentItem> comments) {
        public List<ReviewComment> toReviewComments() {
            return comments == null ? List.of() : comments.stream()
                    .map(c -> new ReviewComment(c.category(), c.severity(),
                            c.filePath(), c.lineNumber(), c.description()))
                    .toList();
        }
    }

    public record CommentItem(String category, String severity, String filePath,
                              Integer lineNumber, String description) {}
}