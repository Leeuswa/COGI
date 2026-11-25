package idu.sba.backend.global.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

// 로그인 토큰 · 2차 인증 임시 토큰 · GitHub 연동 state 는 같은 키로 서명된다.
// 인증 필터는 로그인 토큰만 받아야 한다 — 임시 토큰으로 API 를 쓰거나 /api/auth/refresh 에서 정식 토큰을 받으면 안 된다
class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-secret-key-for-jwt-filter-0123456789abcdef";
    private static final long USER_ID = 42L;

    private JwtProvider jwtProvider;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(SECRET, 60_000L);
        filter = new JwtAuthenticationFilter(jwtProvider);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private Authentication runWithBearer(String token) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/refresh");
        req.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private Authentication runWithCookie(String token) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/users/me");
        req.setCookies(new Cookie(CookieUtil.ACCESS_TOKEN, token));
        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void 로그인_토큰은_그대로_통한다() throws Exception {
        Authentication auth = runWithCookie(jwtProvider.createToken(USER_ID, "USER"));

        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(USER_ID);
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER");
    }

    @Test
    void 로그인_토큰은_Bearer_헤더로도_통한다() throws Exception {
        Authentication auth = runWithBearer(jwtProvider.createToken(USER_ID, "ADMIN"));

        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(USER_ID);
    }

    @Test
    void 이차인증_임시_토큰을_Bearer_로_보내면_로그인으로_치지_않는다() throws Exception {
        Authentication auth = runWithBearer(jwtProvider.createTotpToken(USER_ID));

        assertThat(auth).isNull();
    }

    @Test
    void 이차인증_임시_토큰을_쿠키로_보내도_로그인으로_치지_않는다() throws Exception {
        Authentication auth = runWithCookie(jwtProvider.createTotpToken(USER_ID));

        assertThat(auth).isNull();
    }

    @Test
    void GitHub_연동_state_토큰도_로그인으로_치지_않는다() throws Exception {
        Authentication auth = runWithBearer(jwtProvider.createGithubLinkState(USER_ID));

        assertThat(auth).isNull();
    }

    @Test
    void 이차인증_임시_토큰은_원래_자리인_verify_에서는_계속_쓰인다() {
        String temp = jwtProvider.createTotpToken(USER_ID);

        assertThat(jwtProvider.parseTotpToken(temp)).isEqualTo(USER_ID);
    }
}
