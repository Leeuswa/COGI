package idu.sba.backend.global.ai;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

// 벤더 응답 스키마가 서로 다르고 캐시 토큰이 여러 필드로 쪼개져 있어서, 파싱이 조용히 0이 되는 게 가장 무서운 실패다.
// 두 벤더의 실제 응답 형태를 그대로 넣어 합산이 맞는지 확인한다.
class ProviderUsageClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void OpenAI_results에서_토큰을_합산한다_캐시필드는_중복합산하지_않는다() {
        String json = """
                [{"input_tokens":1200,"output_tokens":340,"input_cached_tokens":200,"num_model_requests":3}]
                """;

        long[] tokens = ProviderUsageClient.sumTokens(objectMapper.readTree(json));

        // input_cached_tokens는 input_tokens의 부분집합이라 더하면 안 된다
        assertThat(tokens[0]).isEqualTo(1200);
        assertThat(tokens[1]).isEqualTo(340);
    }

    @Test
    void Anthropic_results에서_쪼개진_input_토큰을_모두_합산한다() {
        String json = """
                [{"uncached_input_tokens":900,
                  "cache_read_input_tokens":100,
                  "cache_creation":{"ephemeral_5m_input_tokens":50,"ephemeral_1h_input_tokens":10},
                  "output_tokens":250}]
                """;

        long[] tokens = ProviderUsageClient.sumTokens(objectMapper.readTree(json));

        assertThat(tokens[0]).isEqualTo(1060); // 900 + 100 + 50 + 10 (중첩 객체까지)
        assertThat(tokens[1]).isEqualTo(250);
    }

    @Test
    void 비용은_객체형_문자열형_둘_다_읽는다() {
        // OpenAI: amount가 객체({value,currency}) / Anthropic: amount가 문자열
        assertThat(ProviderUsageClient.sumCost(objectMapper.readTree("""
                [{"amount":{"value":0.12,"currency":"usd"}},{"amount":{"value":0.03,"currency":"usd"}}]
                """)))
                .isEqualByComparingTo(new BigDecimal("0.15"));

        assertThat(ProviderUsageClient.sumCost(objectMapper.readTree("""
                [{"amount":"0.34","currency":"USD"}]
                """)))
                .isEqualByComparingTo(new BigDecimal("0.34"));
    }

    @Test
    void 모르는_스키마면_0을_돌려주고_예외는_던지지_않는다() {
        long[] tokens = ProviderUsageClient.sumTokens(objectMapper.readTree("""
                [{"totally":"different","nested":{"shape":true}}]
                """));

        assertThat(tokens[0]).isZero();
        assertThat(tokens[1]).isZero();
        assertThat(ProviderUsageClient.sumCost(objectMapper.readTree("[{}]")))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }
}
