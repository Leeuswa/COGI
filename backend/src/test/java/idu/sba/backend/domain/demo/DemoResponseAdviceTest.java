package idu.sba.backend.domain.demo;

import idu.sba.backend.global.common.ApiResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DemoResponseAdviceTest {

    private static final long DEMO_USER = 100L;
    private static final long DEMO_ADMIN = 200L;
    private static final long REAL_USER = 7L;

    private DemoResponseAdvice advice;

    @BeforeEach
    void setUp() {
        DemoAccountRegistry registry = mock(DemoAccountRegistry.class);
        when(registry.kindOf(DEMO_USER)).thenReturn(DemoKind.USER);
        when(registry.kindOf(DEMO_ADMIN)).thenReturn(DemoKind.ADMIN);
        advice = new DemoResponseAdvice(registry, JsonMapper.builder().build());
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private Object write(long userId, String path, Object body) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
        return advice.beforeBodyWrite(body, null, null, null,
                new ServletServerHttpRequest(new MockHttpServletRequest("GET", path)), null);
    }

    private static ApiResponse<Map<String, Object>> profile() {
        Map<String, Object> p = new HashMap<>();
        p.put("name", "체험 사용자");
        p.put("email", null);
        p.put("githubUsername", "demo.cogi");
        return ApiResponse.ok(p);
    }

    @Test
    void 테스트_계정_프로필에는_화면용_이메일이_들어간다() {
        JsonNode out = (JsonNode) write(DEMO_USER, DemoResponseAdvice.PROFILE_PATH, profile());
        assertThat(out.get("data").get("email").asString()).isEqualTo("demo-user@example.com");
        JsonNode admin = (JsonNode) write(DEMO_ADMIN, DemoResponseAdvice.PROFILE_PATH, profile());
        assertThat(admin.get("data").get("email").asString()).isEqualTo("demo-admin@example.com");
    }

    @Test
    void 진짜_회원_응답은_그대로() {
        ApiResponse<Map<String, Object>> body = profile();
        assertThat(write(REAL_USER, DemoResponseAdvice.PROFILE_PATH, body)).isSameAs(body);
        ApiResponse<List<Object>> empty = ApiResponse.ok(List.of());
        assertThat(write(REAL_USER, DemoResponseAdvice.NOTICES_PATH, empty)).isSameAs(empty);
    }

    @Test
    void 공지가_없을_때만_예시_공지를_보여준다() {
        JsonNode user = (JsonNode) write(DEMO_USER, DemoResponseAdvice.NOTICES_PATH, ApiResponse.ok(List.of()));
        assertThat(user.get("data").size()).isEqualTo(DemoResponseAdvice.SAMPLE_NOTICES.size());
        assertThat(user.get("data").get(0).has("sentAt")).isTrue();

        JsonNode admin = (JsonNode) write(DEMO_ADMIN, DemoResponseAdvice.ADMIN_NOTICES_PATH, ApiResponse.ok(List.of()));
        assertThat(admin.get("data").get(0).get("status").asString()).isEqualTo("SENT");
        assertThat(admin.get("data").get(0).get("id").asLong()).isNegative();

        // 실제 공지가 있으면 손대지 않는다
        JsonNode real = (JsonNode) write(DEMO_USER, DemoResponseAdvice.NOTICES_PATH,
                ApiResponse.ok(List.of(Map.of("id", 1, "subject", "실제 공지"))));
        assertThat(real.get("data").size()).isEqualTo(1);
    }
}
