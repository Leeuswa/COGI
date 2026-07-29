package idu.sba.backend.global.ai;

import idu.sba.backend.domain.admin.dto.ProviderUsageResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 벤더 대시보드의 "실제" 사용량/비용을 Admin API로 가져온다 — 우리 ai_usage_logs 자체집계와는 별개 지표.
 *
 * OpenAI / Anthropic만 지원한다. Gemini는 전용 usage API가 없어(Cloud Monitoring/Billing 우회만 가능) 제외.
 * 일반 API 키로는 조회할 수 없고 조직 Admin 키가 따로 필요하다 — 키가 비어 있으면 그 벤더는 빈 결과를
 * 돌려준다(키 설정 전에도 관리자 화면이 깨지지 않게).
 */
@Slf4j
@Component
public class ProviderUsageClient {

    // 리뷰 호출(AiReviewClientImpl)과 같은 이유로 타임아웃을 명시한다 — 기본 생성자는 무한 대기다.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int DAILY_BUCKET_LIMIT = 31;  // 두 벤더 모두 1d 버킷은 페이지당 31개가 상한
    private static final int MAX_PAGES = 24;           // 무한 페이지네이션 방지 (31 * 24 ≈ 2년)

    private final String openaiAdminKey;
    private final String claudeAdminKey;
    private final RestClient openai;
    private final RestClient claude;

    public ProviderUsageClient(@Value("${ai.openai.adminKey:}") String openaiAdminKey,
                               @Value("${ai.claude.adminKey:}") String claudeAdminKey) {
        this.openaiAdminKey = openaiAdminKey;
        this.claudeAdminKey = claudeAdminKey;

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.openai = RestClient.builder().baseUrl(AiProvider.OPENAI.baseUrl())
                .requestFactory(requestFactory).build();
        this.claude = RestClient.builder().baseUrl(AiProvider.CLAUDE.baseUrl())
                .requestFactory(requestFactory).build();
    }

    /** from~to(포함) 구간의 벤더별·일자별 사용량. 한 벤더가 실패해도 다른 벤더 결과는 그대로 돌려준다. */
    public List<ProviderUsageResponseDTO> fetch(LocalDate from, LocalDate to) {
        List<ProviderUsageResponseDTO> out = new ArrayList<>();
        out.addAll(safeFetch("OPENAI", () -> fetchOpenAi(from, to)));
        out.addAll(safeFetch("CLAUDE", () -> fetchClaude(from, to)));
        out.sort(Comparator.comparing(ProviderUsageResponseDTO::date)
                .thenComparing(ProviderUsageResponseDTO::vendor));
        return out;
    }

    private List<ProviderUsageResponseDTO> safeFetch(String vendor, Supplier<List<ProviderUsageResponseDTO>> f) {
        try {
            return f.get();
        } catch (Exception e) {
            // 키 오류(401)·권한 부족(403)·벤더 장애로 관리자 화면 전체가 죽지 않게 흡수한다.
            log.warn("벤더 사용량 조회 실패 - vendor={}", vendor, e);
            return List.of();
        }
    }

    // ── OpenAI: /organization/usage/completions (토큰) + /organization/costs (비용) ──
    private List<ProviderUsageResponseDTO> fetchOpenAi(LocalDate from, LocalDate to) {
        if (openaiAdminKey == null || openaiAdminKey.isBlank()) return List.of();

        long start = from.atStartOfDay().toInstant(ZoneOffset.UTC).getEpochSecond();
        long end = to.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC).getEpochSecond();
        Map<String, Object> query = Map.of(
                "start_time", start, "end_time", end,
                "bucket_width", "1d", "limit", DAILY_BUCKET_LIMIT);

        Map<LocalDate, Agg> byDate = new LinkedHashMap<>();
        for (JsonNode bucket : buckets(openai, "/organization/usage/completions", query, false, openaiAdminKey)) {
            agg(byDate, openAiBucketDate(bucket)).addTokens(bucket.path("results"));
        }
        for (JsonNode bucket : buckets(openai, "/organization/costs", query, false, openaiAdminKey)) {
            agg(byDate, openAiBucketDate(bucket)).addCost(bucket.path("results"));
        }
        return toDtos("OPENAI", byDate);
    }

    // ── Anthropic: /v1/organizations/usage_report/messages (토큰) + /v1/organizations/cost_report (비용) ──
    private List<ProviderUsageResponseDTO> fetchClaude(LocalDate from, LocalDate to) {
        if (claudeAdminKey == null || claudeAdminKey.isBlank()) return List.of();

        // RFC3339(UTC). ending_at은 to 당일 끝까지 포함하려고 다음날 00:00으로 준다.
        String start = from.atStartOfDay().atOffset(ZoneOffset.UTC).toString();
        String end = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC).toString();

        Map<LocalDate, Agg> byDate = new LinkedHashMap<>();
        Map<String, Object> usageQuery = Map.of(
                "starting_at", start, "ending_at", end, "bucket_width", "1d", "limit", DAILY_BUCKET_LIMIT);
        for (JsonNode bucket : buckets(claude, "/v1/organizations/usage_report/messages", usageQuery, true, claudeAdminKey)) {
            agg(byDate, claudeBucketDate(bucket)).addTokens(bucket.path("results"));
        }
        // cost_report는 bucket_width를 받지 않는다(1일 고정).
        Map<String, Object> costQuery = Map.of("starting_at", start, "ending_at", end, "limit", DAILY_BUCKET_LIMIT);
        for (JsonNode bucket : buckets(claude, "/v1/organizations/cost_report", costQuery, true, claudeAdminKey)) {
            agg(byDate, claudeBucketDate(bucket)).addCost(bucket.path("results"));
        }
        return toDtos("CLAUDE", byDate);
    }

    /** data[] 버킷을 페이지 끝까지 모아 돌려준다. 두 벤더 모두 has_more + next_page(커서) 방식이 같다. */
    private List<JsonNode> buckets(RestClient client, String path, Map<String, Object> query,
                                   boolean anthropic, String key) {
        List<JsonNode> out = new ArrayList<>();
        String page = null;
        for (int i = 0; i < MAX_PAGES; i++) {
            String cursor = page;
            JsonNode res = client.get()
                    .uri(b -> {
                        b.path(path);
                        query.forEach(b::queryParam);
                        if (cursor != null) b.queryParam("page", cursor);
                        return b.build();
                    })
                    .headers(h -> {
                        if (anthropic) {
                            h.set("x-api-key", key);
                            h.set("anthropic-version", ANTHROPIC_VERSION);
                        } else {
                            h.set("Authorization", "Bearer " + key);
                        }
                    })
                    .retrieve()
                    .body(JsonNode.class);

            for (JsonNode bucket : res.path("data")) out.add(bucket);

            page = text(res.path("next_page"));
            if (!res.path("has_more").asBoolean(false) || page == null || page.isBlank()) break;
        }
        return out;
    }

    private static LocalDate openAiBucketDate(JsonNode bucket) {
        return Instant.ofEpochSecond(bucket.path("start_time").asLong(0))
                .atOffset(ZoneOffset.UTC).toLocalDate();
    }

    private static LocalDate claudeBucketDate(JsonNode bucket) {
        String s = text(bucket.path("starting_at"));
        // "2026-01-27T00:00:00Z" → 앞 10자리가 날짜
        return s != null && s.length() >= 10 ? LocalDate.parse(s.substring(0, 10)) : LocalDate.EPOCH;
    }

    private static Agg agg(Map<LocalDate, Agg> byDate, LocalDate date) {
        return byDate.computeIfAbsent(date, d -> new Agg());
    }

    private static List<ProviderUsageResponseDTO> toDtos(String vendor, Map<LocalDate, Agg> byDate) {
        return byDate.entrySet().stream()
                .map(e -> new ProviderUsageResponseDTO(vendor, e.getKey(),
                        e.getValue().inputTokens, e.getValue().outputTokens, e.getValue().cost))
                .toList();
    }

    // 버킷 하나(=하루) 누적치
    private static final class Agg {
        long inputTokens;
        long outputTokens;
        BigDecimal cost;

        void addTokens(JsonNode results) {
            long[] t = sumTokens(results);
            inputTokens += t[0];
            outputTokens += t[1];
        }

        void addCost(JsonNode results) {
            BigDecimal c = sumCost(results);
            cost = cost == null ? c : cost.add(c);
        }
    }

    /**
     * 이름이 *input_tokens / *output_tokens 로 끝나는 숫자 리프를 전부 더한다. 반환 [input, output].
     *
     * 벤더가 캐시 토큰을 여러 필드로 쪼개거나(Anthropic: uncached_input_tokens / cache_read_input_tokens /
     * cache_creation.ephemeral_*_input_tokens) 스키마를 바꿔도 집계가 조용히 0이 되지 않게 하려는 방어적 파싱.
     * OpenAI의 input_cached_tokens처럼 input_tokens의 부분집합인 필드는 이름이 안 걸려 자동으로 빠진다(중복합산 방지).
     */
    static long[] sumTokens(JsonNode node) {
        long[] acc = new long[2];
        walkTokens(node, acc);
        return acc;
    }

    private static void walkTokens(JsonNode node, long[] acc) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> e : node.properties()) {
                String name = e.getKey();
                JsonNode v = e.getValue();
                if (v.isNumber()) {
                    if (name.endsWith("input_tokens")) acc[0] += v.asLong();
                    else if (name.endsWith("output_tokens")) acc[1] += v.asLong();
                } else {
                    walkTokens(v, acc);
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) walkTokens(child, acc);
        }
    }

    /**
     * amount 필드를 전부 더한다. 벤더별로 형태가 달라 세 경우를 받는다:
     * OpenAI {"amount":{"value":0.12,"currency":"usd"}} · Anthropic {"amount":"0.34"} · 숫자 그대로.
     */
    static BigDecimal sumCost(JsonNode node) {
        BigDecimal[] acc = { BigDecimal.ZERO };
        walkCost(node, acc);
        return acc[0];
    }

    private static void walkCost(JsonNode node, BigDecimal[] acc) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> e : node.properties()) {
                JsonNode v = e.getValue();
                if ("amount".equals(e.getKey())) {
                    acc[0] = acc[0].add(v.isObject() ? decimal(v.path("value")) : decimal(v));
                } else {
                    walkCost(v, acc);
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) walkCost(child, acc);
        }
    }

    private static BigDecimal decimal(JsonNode v) {
        if (v.isNumber()) return v.decimalValue();
        String s = text(v);
        if (s == null || s.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    // 문자열이 아닌 노드에서 asString()이 던지는 경우까지 막는다 — 스키마가 달라도 파싱이 죽지 않게.
    private static String text(JsonNode v) {
        try {
            return v.isNull() || v.isMissingNode() ? null : v.asString();
        } catch (Exception e) {
            return null;
        }
    }
}
