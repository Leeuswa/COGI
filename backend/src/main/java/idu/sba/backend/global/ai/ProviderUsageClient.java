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
import java.util.Collection;
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
    // 벤더별 비용 단위: OpenAI는 달러, Anthropic은 센트로 보고한다(문서: "decimal strings in lowest units (cents)").
    private static final BigDecimal USD_PER_USD = BigDecimal.ONE;
    private static final BigDecimal CENTS_PER_USD = BigDecimal.valueOf(100);

    private final String openaiAdminKey;
    private final String claudeAdminKey;
    // 비어 있으면 조직 전체를 조회한다. 값을 넣으면 그 API 키들의 사용량만 남아 우리 앱 사용분만 본다
    // (벤더 대시보드는 Workbench·다른 앱까지 합산하므로, 숫자를 맞추려면 이 필터가 필요하다).
    private final List<String> openaiApiKeyIds;
    private final List<String> claudeApiKeyIds;
    // Claude 전용 워크스페이스 id. 값이 있으면 토큰·비용 모두 이 워크스페이스로만 좁힌다.
    // cost_report는 api_key 필터를 지원 안 해서(비용은 워크스페이스 단위로만 갈라짐), 키별 비용을 정확히
    // 맞추려면 워크스페이스로 분리하는 수밖에 없다. 이 값이 있으면 claudeApiKeyIds는 무시된다.
    private final String claudeWorkspaceId;
    private final RestClient openai;
    private final RestClient claude;

    // 벤더 Admin API는 rate limit이 빡빡한데, 화면 한 번 열면 (토큰+비용)×2벤더로 최대 ~96콜을 때린다.
    // 관리자가 새로고침 몇 번 하면 429로 막히므로, 같은 (from,to) 결과를 잠깐 재사용한다.
    // ponytail: 인메모리 단일 컨테이너 캐시. 여러 인스턴스로 뜨거나 실시간성이 필요하면 Redis로 승격.
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final int CACHE_MAX_ENTRIES = 64;  // 관리자가 스크립트로 범위를 무한정 바꿔도 무한 증가 방지
    private final Map<String, Cached> cache = new java.util.concurrent.ConcurrentHashMap<>();

    private record Cached(Instant at, List<ProviderUsageResponseDTO> data) {}

    public ProviderUsageClient(@Value("${ai.openai.adminKey:}") String openaiAdminKey,
                               @Value("${ai.claude.adminKey:}") String claudeAdminKey,
                               @Value("${ai.openai.apiKeyIds:}") String openaiApiKeyIds,
                               @Value("${ai.claude.apiKeyIds:}") String claudeApiKeyIds,
                               @Value("${ai.claude.workspaceId:}") String claudeWorkspaceId) {
        this.openaiAdminKey = openaiAdminKey;
        this.claudeAdminKey = claudeAdminKey;
        this.openaiApiKeyIds = splitIds(openaiApiKeyIds);
        this.claudeApiKeyIds = splitIds(claudeApiKeyIds);
        this.claudeWorkspaceId = claudeWorkspaceId == null ? "" : claudeWorkspaceId.trim();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.openai = RestClient.builder().baseUrl(AiProvider.OPENAI.baseUrl())
                .requestFactory(requestFactory).build();
        this.claude = RestClient.builder().baseUrl(AiProvider.CLAUDE.baseUrl())
                .requestFactory(requestFactory).build();
    }

    // "apikey_01ABC, apikey_01DEF" → ["apikey_01ABC", "apikey_01DEF"]. 비었으면 빈 리스트(=필터 없음).
    private static List<String> splitIds(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** from~to(포함) 구간의 벤더별·일자별 사용량. 한 벤더가 실패해도 다른 벤더 결과는 그대로 돌려준다. */
    public List<ProviderUsageResponseDTO> fetch(LocalDate from, LocalDate to) {
        String key = from + "|" + to;
        Cached hit = cache.get(key);
        if (hit != null && Duration.between(hit.at(), Instant.now()).compareTo(CACHE_TTL) < 0) {
            return hit.data();
        }
        List<ProviderUsageResponseDTO> data = fetchUncached(from, to);
        // 전부 빈 결과(키 미설정 or 두 벤더 다 실패)는 캐시 안 함 — 일시적 429가 10분 고정되지 않게.
        if (!data.isEmpty()) {
            if (cache.size() >= CACHE_MAX_ENTRIES) cache.clear();  // 단순 상한: 넘으면 통째로 비운다
            cache.put(key, new Cached(Instant.now(), data));
        }
        return data;
    }

    private List<ProviderUsageResponseDTO> fetchUncached(LocalDate from, LocalDate to) {
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
        Map<String, Object> query = new LinkedHashMap<>(Map.of(
                "start_time", start, "end_time", end,
                "bucket_width", "1d", "limit", DAILY_BUCKET_LIMIT));
        if (!openaiApiKeyIds.isEmpty()) query.put("api_key_ids[]", openaiApiKeyIds);

        Map<LocalDate, Agg> byDate = new LinkedHashMap<>();
        for (JsonNode bucket : buckets(openai, "/organization/usage/completions", query, false, openaiAdminKey)) {
            agg(byDate, openAiBucketDate(bucket)).addTokens(bucket.path("results"));
        }
        for (JsonNode bucket : buckets(openai, "/organization/costs", query, false, openaiAdminKey)) {
            agg(byDate, openAiBucketDate(bucket)).addCost(bucket.path("results"), USD_PER_USD);
        }
        return toDtos("OPENAI", byDate);
    }

    // ── Anthropic: /v1/organizations/usage_report/messages (토큰) + /v1/organizations/cost_report (비용) ──
    private List<ProviderUsageResponseDTO> fetchClaude(LocalDate from, LocalDate to) {
        if (claudeAdminKey == null || claudeAdminKey.isBlank()) return List.of();

        // RFC3339(UTC). ending_at은 to 당일 끝까지 포함하려고 다음날 00:00으로 준다.
        String start = from.atStartOfDay().atOffset(ZoneOffset.UTC).toString();
        String end = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC).toString();

        boolean byWorkspace = !claudeWorkspaceId.isBlank();

        Map<LocalDate, Agg> byDate = new LinkedHashMap<>();
        // 토큰: 워크스페이스가 지정되면 그걸로 좁히고(가장 정확), 아니면 기존 api_key_ids 필터로 대체.
        Map<String, Object> usageQuery = new LinkedHashMap<>(Map.of(
                "starting_at", start, "ending_at", end, "bucket_width", "1d", "limit", DAILY_BUCKET_LIMIT));
        if (byWorkspace) usageQuery.put("workspace_ids[]", List.of(claudeWorkspaceId));
        else if (!claudeApiKeyIds.isEmpty()) usageQuery.put("api_key_ids[]", claudeApiKeyIds);
        for (JsonNode bucket : buckets(claude, "/v1/organizations/usage_report/messages", usageQuery, true, claudeAdminKey)) {
            agg(byDate, claudeBucketDate(bucket)).addTokens(bucket.path("results"));
        }
        // 비용: cost_report는 bucket_width도 api_key 필터도 안 받는다(1일 고정, 워크스페이스 단위로만 갈라짐).
        // 워크스페이스가 지정되면 group_by=workspace_id로 쪼갠 뒤 그 워크스페이스 몫만 합산 = 대시보드와 정확히 일치.
        // 없으면 조직 전체 비용(예전 동작 그대로).
        Map<String, Object> costQuery = new LinkedHashMap<>(Map.of(
                "starting_at", start, "ending_at", end, "limit", DAILY_BUCKET_LIMIT));
        if (byWorkspace) costQuery.put("group_by[]", List.of("workspace_id"));
        for (JsonNode bucket : buckets(claude, "/v1/organizations/cost_report", costQuery, true, claudeAdminKey)) {
            JsonNode results = bucket.path("results");
            BigDecimal cents = byWorkspace ? sumCostForWorkspace(results, claudeWorkspaceId) : sumCost(results);
            agg(byDate, claudeBucketDate(bucket)).addCentsCost(cents);
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
                        // 리스트 값은 같은 이름으로 여러 번 붙어야 한다(api_key_ids[]=a&api_key_ids[]=b).
                        // 그냥 넘기면 "[a, b]" 한 덩어리로 인코딩돼 필터가 통째로 무시된다.
                        query.forEach((k, v) -> {
                            if (v instanceof Collection<?> c) b.queryParam(k, c);
                            else b.queryParam(k, v);
                        });
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

        // divisor로 벤더의 통화 단위를 USD에 맞춘다 — OpenAI는 달러(1), Anthropic은 센트(100)로 보고한다.
        void addCost(JsonNode results, BigDecimal divisor) {
            BigDecimal c = sumCost(results).divide(divisor, 6, java.math.RoundingMode.HALF_UP);
            cost = cost == null ? c : cost.add(c);
        }

        // 이미 합산된 센트값을 USD로 환산해 누적 (Anthropic 비용 전용).
        void addCentsCost(BigDecimal cents) {
            BigDecimal c = cents.divide(CENTS_PER_USD, 6, java.math.RoundingMode.HALF_UP);
            cost = cost == null ? c : cost.add(c);
        }
    }

    // cost_report를 group_by=workspace_id로 쪼갰을 때, 지정한 워크스페이스 몫(센트)만 합산한다.
    // results[]의 각 항목은 workspace_id + amount(센트 문자열)를 갖는다.
    static BigDecimal sumCostForWorkspace(JsonNode results, String workspaceId) {
        BigDecimal acc = BigDecimal.ZERO;
        if (results != null && results.isArray()) {
            for (JsonNode r : results) {
                if (workspaceId.equals(text(r.path("workspace_id")))) {
                    acc = acc.add(decimal(r.path("amount")));
                }
            }
        }
        return acc;
    }

    // 벤더 대시보드의 "총 입력 토큰"과 숫자를 맞추기 위해, 캐시 토큰을 뺀 순수 입력만 센다.
    // Anthropic은 input을 uncached / cache_read / cache_creation.ephemeral_* 로 쪼개 보고하는데,
    // 대시보드 헤드라인 수치는 uncached만 집계한다. cache_read를 더하면 실측이 대시보드의 수십 배로 벌어진다.
    private static final java.util.Set<String> INPUT_TOKEN_FIELDS =
            java.util.Set.of("input_tokens", "uncached_input_tokens");

    /**
     * 입력/출력 토큰을 합산한다. 반환 [input, output].
     *
     * 세는 대상: input_tokens(OpenAI) · uncached_input_tokens(Anthropic) · output_tokens.
     * 제외: cache_read_input_tokens, cache_creation.* (캐시 토큰), OpenAI의 input_cached_tokens(부분집합).
     * 중첩 객체도 훑기 때문에 스키마가 조금 달라져도 필드명이 같으면 계속 집계된다.
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
                // cache_creation 하위에는 ephemeral_*_input_tokens가 들어 있다 — 통째로 건너뛴다.
                if ("cache_creation".equals(name)) continue;
                if (v.isNumber()) {
                    if (INPUT_TOKEN_FIELDS.contains(name)) acc[0] += v.asLong();
                    else if ("output_tokens".equals(name)) acc[1] += v.asLong();
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
