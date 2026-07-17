package idu.sba.backend.global.ai;

import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AiReviewClientImpl implements AiReviewClient {

    private final ObjectMapper objectMapper;
    private final Map<AiProvider, String> apiKeys = new EnumMap<>(AiProvider.class);
    private final Map<AiProvider, RestClient> clients = new EnumMap<>(AiProvider.class);

    public AiReviewClientImpl(ObjectMapper objectMapper,
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

    // systemPrompt는 호출부(비로그인 체험 / 로그인 리뷰 파이프라인)가 이미 완성해서 넘긴다.
    // 이 클라이언트는 "어느 벤더로, 어떻게 호출하고, 응답을 어떻게 파싱하는지"만 책임진다.
    @Override
    public AiReviewResult review(AiModel model, String systemPrompt, String code, String language) {
        String userContent = buildUserContent(code, language);
        try {
            AiProvider provider = model.provider();
            VendorCallResult result = provider.anthropicStyle()
                    ? callAnthropic(provider, model.id(), systemPrompt, userContent)
                    : callOpenAiCompatible(provider, model.id(), systemPrompt, userContent);

            AiJsonResponse parsed = objectMapper.readValue(stripCodeFence(result.content()), AiJsonResponse.class);
            List<AiReviewIssue> issues = parsed.issues() == null ? List.of() : parsed.issues().stream()
                    .map(i -> new AiReviewIssue(i.category(), i.severity(), i.filePath(), i.lineNumber(), i.description()))
                    .toList();
            double cost = model.calculateCost(result.inputTokens(), result.outputTokens());

            return new AiReviewResult(parsed.summary(), issues, result.inputTokens(), result.outputTokens(), cost);
        } catch (RestClientResponseException e) { //4xx/5xx — 벤더가 응답은 했지만 실패로 응답한 경우
            log.error("AI 모델 호출 실패 - model={}, provider={}, status={}, body={}",
                    model.id(), model.provider(), e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED);
        } catch (Exception e) { //연결 실패, JSON 파싱 실패(AI가 지시한 스키마를 벗어난 응답 포함) 등
            log.error("AI 리뷰 처리 실패 - model={}, provider={}", model.id(), model.provider(), e);
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED);
        }
    }

    private String buildUserContent(String code, String language) {
        return (language != null && !language.isBlank() ? "언어: " + language + "\n\n" : "") + "코드:\n" + code;
    }

    // "JSON으로만 답하라"고 지시해도 모델이 ```json ... ``` 코드펜스로 감싸는 경우가 흔해서 방어적으로 벗겨낸다
    private String stripCodeFence(String content) {
        String trimmed = content.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstNewline == -1 || lastFence <= firstNewline) {
            return trimmed;
        }
        return trimmed.substring(firstNewline + 1, lastFence).trim();
    }

    // OpenAI 호환 (Gemini/Groq/OpenAI)
    private VendorCallResult callOpenAiCompatible(AiProvider p, String modelId, String systemPrompt, String userContent) {
        // OpenAI는 신형 모델부터 max_tokens를 거부하고 max_completion_tokens를 요구함(400
        // unsupported_parameter로 확인됨). Gemini/Groq는 아직 이 문제가 없어 기존 max_tokens 유지.
        Map<String, Object> body = new HashMap<>(Map.of(
                "model", modelId,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userContent)),
                "response_format", Map.of("type", "json_object")
        ));
        body.put(p == AiProvider.OPENAI ? "max_completion_tokens" : "max_tokens", 8192);

        JsonNode res = clients.get(p).post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKeys.get(p))
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        String content = res.path("choices").get(0).path("message").path("content").asString();
        int inputTokens = res.path("usage").path("prompt_tokens").asInt(0);
        int outputTokens = res.path("usage").path("completion_tokens").asInt(0);
        return new VendorCallResult(content, inputTokens, outputTokens);
    }

    // Claude
    private VendorCallResult callAnthropic(AiProvider p, String modelId, String systemPrompt, String userContent) {
        Map<String, Object> body = Map.of(
                "model", modelId,
                "max_tokens", 8192, // 이슈가 많은 리뷰는 JSON이 길어져서 2048이면 응답이 중간에 잘림(파싱 실패) — 여유 있게 상향
                "system", systemPrompt,
                "messages", List.of(Map.of("role", "user", "content", userContent))
        );
        JsonNode res = clients.get(p).post()
                .uri("/v1/messages")
                .header("x-api-key", apiKeys.get(p))
                .header("anthropic-version", "2023-06-01")
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        String content = res.path("content").get(0).path("text").asString();
        int inputTokens = res.path("usage").path("input_tokens").asInt(0);
        int outputTokens = res.path("usage").path("output_tokens").asInt(0);
        return new VendorCallResult(content, inputTokens, outputTokens);
    }

    // 벤더 호출 1건의 결과(본문 텍스트 + 토큰 사용량) — 두 호출 경로가 공유
    private record VendorCallResult(String content, int inputTokens, int outputTokens) {}

    // AI 응답 JSON 파싱용 — 프롬프트가 이 형태로 답하도록 지시한다(공용 계약)
    private record AiJsonResponse(String summary, List<IssueJson> issues) {}

    private record IssueJson(String category, String severity, String filePath,
                              Integer lineNumber, String description) {}

}
