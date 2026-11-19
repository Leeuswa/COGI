package idu.sba.backend.domain.demo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import idu.sba.backend.domain.repo.entity.GithubRepository;
import idu.sba.backend.domain.repo.repository.GithubRepositoryRepository;
import idu.sba.backend.domain.repo.service.RepoMemberService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DemoGuardFilterTest {

    private static final long DEMO_USER = 100L;
    private static final long DEMO_ADMIN = 200L;
    private static final long REAL_USER = 7L;

    private DemoAccountRegistry registry;
    private JdbcTemplate jdbc;
    private DemoGuardFilter filter;
    private GithubRepositoryRepository repoRepository;
    private RepoMemberService repoMemberService;

    @BeforeEach
    void setUp() {
        registry = mock(DemoAccountRegistry.class);
        when(registry.isDemo(DEMO_USER)).thenReturn(true);
        when(registry.isDemo(DEMO_ADMIN)).thenReturn(true);
        when(registry.isDemoAdmin(DEMO_ADMIN)).thenReturn(true);
        when(registry.kindOf(DEMO_USER)).thenReturn(DemoKind.USER);
        when(registry.kindOf(DEMO_ADMIN)).thenReturn(DemoKind.ADMIN);
        jdbc = mock(JdbcTemplate.class);
        repoRepository = mock(GithubRepositoryRepository.class);
        repoMemberService = mock(RepoMemberService.class);
        filter = new DemoGuardFilter(registry, jdbc, JsonMapper.builder().build(), repoRepository, repoMemberService,
                mock(PlatformTransactionManager.class));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse run(long userId, String method, String path, String ip) throws Exception {
        return run(userId, method, path, ip, null);
    }

    private MockHttpServletResponse run(long userId, String method, String path, String ip, String json) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
        MockHttpServletRequest req = new MockHttpServletRequest(method, path);
        req.setRemoteAddr(ip);
        if (json != null) {
            req.setContentType("application/json");
            req.setContent(json.getBytes(StandardCharsets.UTF_8));
        }
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, res, chain);
        if (chain.getRequest() != null) res.setStatus(299); // 다음 단계로 넘어갔다는 표시
        return res;
    }

    @Test
    void 진짜_회원_요청은_건드리지_않는다() throws Exception {
        assertThat(run(REAL_USER, "DELETE", "/api/users/me", "1.1.1.1").getStatus()).isEqualTo(299);
        assertThat(run(REAL_USER, "POST", "/api/payments/methods", "1.1.1.1").getStatus()).isEqualTo(299);
        assertThat(run(REAL_USER, "GET", "/api/repos/github", "1.1.1.1").getStatus()).isEqualTo(299);
    }

    @Test
    void 테스트_관리자는_관리자_콘솔을_읽기만_한다() throws Exception {
        assertThat(run(DEMO_ADMIN, "GET", "/api/admin/members", "2.2.2.2").getStatus()).isEqualTo(299);
        for (String[] w : new String[][]{
                {"PATCH", "/api/admin/members/3/status"}, {"PATCH", "/api/admin/members/3/role"},
                {"DELETE", "/api/admin/members/3"}, {"POST", "/api/admin/notices/email"},
                {"DELETE", "/api/admin/notices/-1"}, {"DELETE", "/api/admin/faqs/1"},
                {"PUT", "/api/admin/review-guidelines/BEGINNER"}, {"PUT", "/api/admin/terms/1"},
                {"POST", "/api/admin/faqs"}}) {
            MockHttpServletResponse res = run(DEMO_ADMIN, w[0], w[1], "2.2.2.2");
            assertThat(res.getStatus()).as(w[0] + " " + w[1]).isEqualTo(403);
            assertThat(res.getContentAsString()).contains(DemoGuardFilter.MSG_ADMIN_READ_ONLY);
        }
        // 자기 사용자 기능은 유저 테스트 계정과 같이 된다 (다시 로그인하면 되돌아감)
        assertThat(run(DEMO_ADMIN, "POST", "/api/auth/logout", "2.2.2.2").getStatus()).isEqualTo(299);
        assertThat(run(DEMO_ADMIN, "PUT", "/api/users/me/pet", "2.2.2.2").getStatus()).isEqualTo(299);
        assertThat(run(DEMO_ADMIN, "PATCH", "/api/learning-cards/3/bookmark", "2.2.2.2").getStatus()).isEqualTo(299);
        // 결제 · 탈퇴 같은 막힌 기능은 관리자도 막힌다
        assertThat(run(DEMO_ADMIN, "DELETE", "/api/users/me", "2.2.2.2").getStatus()).isEqualTo(403);
    }

    @Test
    void 테스트_유저도_돈_메일_외부계정_기능은_막힌다() throws Exception {
        for (String[] w : new String[][]{
                {"POST", "/api/payments/methods"}, {"POST", "/api/subscriptions"}, {"PATCH", "/api/subscriptions/3"},
                {"DELETE", "/api/subscriptions/3"}, {"POST", "/api/subscriptions/3/resume"},
                {"DELETE", "/api/users/me"}, {"PATCH", "/api/users/me/password"},
                {"POST", "/api/users/me/totp/setup"}, {"POST", "/api/users/me/github-link"},
                {"POST", "/api/repos/123/link"},
                {"POST", "/api/calendar/kakao"}, {"POST", "/api/inquiries"}}) {
            assertThat(run(DEMO_USER, w[0], w[1], "3.3.3.3").getStatus()).as(w[0] + " " + w[1]).isEqualTo(403);
        }
        // 되돌릴 수 있는 기능은 그대로 된다
        assertThat(run(DEMO_USER, "PATCH", "/api/users/me/profile", "3.3.3.3").getStatus()).isEqualTo(299);
        assertThat(run(DEMO_USER, "PUT", "/api/users/me/pet", "3.3.3.3").getStatus()).isEqualTo(299);
        assertThat(run(DEMO_USER, "PATCH", "/api/issues/9/finalize", "3.3.3.3").getStatus()).isEqualTo(299);
    }

    @Test
    void AI_호출은_접속주소마다_하루_10번() throws Exception {
        for (int i = 0; i < DemoGuardFilter.AI_LIMIT_PER_IP; i++) {
            assertThat(run(DEMO_USER, "POST", "/api/learning-cards", "4.4.4.4").getStatus()).isEqualTo(299);
        }
        MockHttpServletResponse over = run(DEMO_USER, "POST", "/api/reviews/paste", "4.4.4.4");
        assertThat(over.getStatus()).isEqualTo(429);
        assertThat(over.getContentAsString()).contains(DemoGuardFilter.MSG_AI_LIMIT);
        // 다른 주소는 따로 센다
        assertThat(run(DEMO_USER, "POST", "/api/reviews/paste", "5.5.5.5").getStatus()).isEqualTo(299);
        // AI 가 아닌 쓰기(퀴즈 제출)는 세지 않는다
        assertThat(run(DEMO_USER, "POST", "/api/learning-cards/1/quiz/2/submit", "4.4.4.4").getStatus()).isEqualTo(299);
    }

    @Test
    void AI_호출은_전체로도_하루_60번() {
        for (int i = 0; i < DemoGuardFilter.AI_LIMIT_TOTAL; i++) {
            assertThat(filter.tryCountAiCall("6.6." + (i / 10) + "." + i)).isTrue();
        }
        assertThat(filter.tryCountAiCall("7.7.7.7")).isFalse();
    }

    @Test
    void GitHub_실시간_화면은_예시로_답한다() throws Exception {
        MockHttpServletResponse repos = run(DEMO_USER, "GET", "/api/repos/github", "8.8.8.8");
        assertThat(repos.getStatus()).isEqualTo(200);
        assertThat(repos.getContentAsString()).contains("sample-shop-api", "sample-board", "sample-batch-jobs");

        when(jdbc.queryForList(anyString(), eq(Long.class), any(Object[].class))).thenReturn(List.of(DEMO_USER));
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(55L))).thenReturn(List.of(DEMO_USER));
        MockHttpServletResponse prs = run(DEMO_USER, "GET", "/api/repos/55/prs", "8.8.8.8");
        assertThat(prs.getStatus()).isEqualTo(200);
        assertThat(prs.getContentAsString()).contains("\"number\":127", "\"number\":128", "demo.dotori", "demo.cogi");

        MockHttpServletResponse files = run(DEMO_USER, "GET", "/api/repos/55/prs/127/files", "8.8.8.8");
        assertThat(files.getContentAsString()).contains("OrderCancelService.java", "@@ -0,0 +1,28 @@");
    }

    @Test
    void 남의_저장소_번호면_예시로_답하지_않는다() throws Exception {
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(77L))).thenReturn(List.of(REAL_USER));
        assertThat(run(DEMO_USER, "GET", "/api/repos/77/prs", "9.9.9.9").getStatus()).isEqualTo(299);
    }

    @Test
    void 예시_저장소만_연동되고_실제_GitHub_은_부르지_않는다() throws Exception {
        String unlinked = String.valueOf(DemoKind.USER.githubRepoIdBase() + 3);
        when(repoRepository.save(any(GithubRepository.class))).thenAnswer(inv -> inv.getArgument(0));
        MockHttpServletResponse ok = run(DEMO_USER, "POST", "/api/repos/" + unlinked + "/link", "10.0.0.1");
        assertThat(ok.getStatus()).isEqualTo(201);
        assertThat(ok.getContentAsString()).contains("sample-batch-jobs", "레포를 연동했습니다.");
        verify(repoRepository).save(any(GithubRepository.class));

        when(repoRepository.existsByGithubRepoId(unlinked)).thenReturn(true);
        assertThat(run(DEMO_USER, "POST", "/api/repos/" + unlinked + "/link", "10.0.0.1").getStatus()).isEqualTo(409);

        // 예시 목록에 없는 번호(= 실제 GitHub 저장소)는 막는다
        MockHttpServletResponse real = run(DEMO_USER, "POST", "/api/repos/123456/link", "10.0.0.1");
        assertThat(real.getStatus()).isEqualTo(403);
        assertThat(real.getContentAsString()).contains(DemoGuardFilter.MSG_REAL_REPO);
        // 관리자 쪽 예시 번호를 유저가 넣어도 막는다
        String adminSample = String.valueOf(DemoKind.ADMIN.githubRepoIdBase() + 3);
        assertThat(run(DEMO_USER, "POST", "/api/repos/" + adminSample + "/link", "10.0.0.1").getStatus()).isEqualTo(403);
    }

    @Test
    void 팀_초대는_저장도_메일도_없이_보낸_것처럼_답한다() throws Exception {
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(55L))).thenReturn(List.of(DEMO_USER));
        MockHttpServletResponse sent = run(DEMO_USER, "POST", "/api/repos/55/members/invite", "11.0.0.1",
                "{\"githubUsername\":\"someone\",\"email\":\"someone@example.org\"}");
        assertThat(sent.getStatus()).isEqualTo(201);
        assertThat(sent.getContentAsString()).contains("\"invitedGithubUsername\":\"someone\"", "\"viaEmail\":true");
        verifyNoInteractions(repoMemberService);

        // 내 저장소가 아니면 원래 길(팀장 확인)로 넘긴다
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(77L))).thenReturn(List.of(REAL_USER));
        assertThat(run(DEMO_USER, "POST", "/api/repos/77/members/invite", "11.0.0.1", "{}").getStatus()).isEqualTo(299);
    }

    @Test
    void 초대_수락은_테스트_계정끼리의_저장소만() throws Exception {
        when(jdbc.queryForList(anyString(), eq(String.class), eq(88L))).thenReturn(List.of("12345678"));
        MockHttpServletResponse real = run(DEMO_USER, "POST", "/api/repos/88/members/invitations/4/accept", "12.0.0.1");
        assertThat(real.getStatus()).isEqualTo(403);
        assertThat(real.getContentAsString()).contains(DemoGuardFilter.MSG_REAL_INVITE);

        when(jdbc.queryForList(anyString(), eq(String.class), eq(89L))).thenReturn(List.of("cogi-demo:user-mate2"));
        assertThat(run(DEMO_USER, "POST", "/api/repos/89/members/invitations/5/reject", "12.0.0.1").getStatus()).isEqualTo(299);
    }

    @Test
    void 미리보기_파일도_예시로_답한다() throws Exception {
        assertThat(DemoGuardFilter.sampleFile("css/app.css").getEncoding()).isEqualTo("utf-8");
        assertThat(DemoGuardFilter.sampleFile("img/logo.png").getEncoding()).isEqualTo("base64");
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(55L))).thenReturn(List.of(DEMO_USER));
        MockHttpServletResponse res = run(DEMO_USER, "GET", "/api/repos/55/file-content", "13.0.0.1");
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getContentAsString()).contains("\"encoding\":\"base64\"");
    }
}
