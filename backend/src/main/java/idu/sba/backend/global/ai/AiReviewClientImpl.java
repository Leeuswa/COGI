package idu.sba.backend.global.ai;

import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AiReviewClientImpl implements AiReviewClient {

    // 이 프로젝트 클래스패스엔 Apache HttpComponents/Jetty/Reactor Netty가 없어서
    // RestClient.builder().build()는 JdkClientHttpRequestFactory로 폴백되는데, 이건 기본 생성자로 쓰면
    // connect/read 타임아웃이 전혀 없다 — 벤더가 고부하 등으로 응답 없이 멈추면 요청이 무한 대기하게 된다
    // (Gemini 503 재시도 중 스튜디오가 "코드 분석 중..."에서 안 멈추던 문제의 원인). 명시적으로 타임아웃을 건다.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

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

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        requestFactory.setReadTimeout(READ_TIMEOUT);
        for (AiProvider p : AiProvider.values()) {
            clients.put(p, RestClient.builder().baseUrl(p.baseUrl()).requestFactory(requestFactory).build());
        }
    }

    // systemPrompt는 호출부(비로그인 체험 / 로그인 리뷰 파이프라인)가 이미 완성해서 넘긴다.
    // 이 클라이언트는 "어느 벤더로, 어떻게 호출하고, 응답을 어떻게 파싱하는지"만 책임진다.
    @Override
    public AiReviewResult review(AiModel model, String systemPrompt, String code, String language, AiInputType inputType) {
        String userContent = buildUserContent(code, language, inputType);
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
            boolean analyzable = parsed.analyzable() == null || parsed.analyzable(); // 필드 누락(구프롬프트/게스트) 시 기본값 true

            return new AiReviewResult(parsed.summary(), issues, result.inputTokens(), result.outputTokens(), cost, analyzable);
        } catch (RestClientResponseException e) { //4xx/5xx — 벤더가 응답은 했지만 실패로 응답한 경우
            log.error("AI 모델 호출 실패 - model={}, provider={}, status={}, body={}",
                    model.id(), model.provider(), e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED);
        } catch (Exception e) { //연결 실패, JSON 파싱 실패(AI가 지시한 스키마를 벗어난 응답 포함) 등
            log.error("AI 리뷰 처리 실패 - model={}, provider={}", model.id(), model.provider(), e);
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED);
        }
    }

    // 범용 생성 호출 — review()와 벤더 호출 경로(callAnthropic/callOpenAiCompatible)·코드펜스 제거를 그대로 재사용하되,
    // 응답을 리뷰 스키마로 파싱하지 않고 원문만 돌려준다. 파싱은 학습카드 등 호출 도메인이 맡는다.
    @Override
    public AiGenerationResult generate(AiModel model, String systemPrompt, String userContent) {
        try {
            AiProvider provider = model.provider();
            VendorCallResult result = provider.anthropicStyle()
                    ? callAnthropic(provider, model.id(), systemPrompt, userContent)
                    : callOpenAiCompatible(provider, model.id(), systemPrompt, userContent);

            double cost = model.calculateCost(result.inputTokens(), result.outputTokens());
            return new AiGenerationResult(stripCodeFence(result.content()), result.inputTokens(), result.outputTokens(), cost);
        } catch (RestClientResponseException e) { //4xx/5xx — 벤더가 실패로 응답
            log.error("AI 생성 호출 실패 - model={}, provider={}, status={}, body={}",
                    model.id(), model.provider(), e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED);
        } catch (Exception e) { //연결 실패 등
            log.error("AI 생성 처리 실패 - model={}, provider={}", model.id(), model.provider(), e);
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED);
        }
    }

    // input_type은 _common_rules.txt 0번 규칙이 참조하는 값 — 프롬프트가 pr_diff/pasted_code를
    // 구분해 다르게 행동하도록 이미 지시돼 있었는데, 실제로 이 값을 보낸 적이 없었던 걸 PR 리뷰를
    // 만들며 발견해 같이 고침.
    private String buildUserContent(String code, String language, AiInputType inputType) {
        return "input_type: " + inputType.wireValue() + "\n\n"
                + (language != null && !language.isBlank() ? "언어: " + language + "\n\n" : "")
                + "코드:\n" + code;
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
    // analyzable은 Boolean(래퍼)으로 받아 필드 자체가 없는 응답(구버전 프롬프트, 게스트 인라인 프롬프트)과
    // 명시적 false를 구분한다 — null이면 review()에서 true로 간주한다.
    private record AiJsonResponse(String summary, Boolean analyzable, List<IssueJson> issues) {}

    private record IssueJson(String category, String severity, String filePath,
                              Integer lineNumber, String description) {}

}
