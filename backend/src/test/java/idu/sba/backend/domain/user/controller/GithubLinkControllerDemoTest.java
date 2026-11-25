package idu.sba.backend.domain.user.controller;

import idu.sba.backend.domain.repo.service.RepoMemberService;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.repository.UserRepository;
import idu.sba.backend.global.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 테스트 로그인 계정(githubId 가 cogi-demo: 로 시작)은 GitHub 연동 콜백에서 실제 GitHub 으로 바뀌면 안 된다.
// 바뀌면 다음 테스트 로그인 때 새 계정이 생기고, 옛 계정은 역할(체험 관리자면 ADMIN)을 가진 일반 계정으로 남는다
class GithubLinkControllerDemoTest {

    private static final String SECRET = "test-secret-key-for-github-link-demo-0123456789";
    private static final long DEMO_ADMIN_ID = 200L;

    private JwtProvider jwtProvider;
    private UserRepository userRepository;
    private RepoMemberService repoMemberService;
    private GithubLinkController controller;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(SECRET, 60_000L);
        userRepository = mock(UserRepository.class);
        repoMemberService = mock(RepoMemberService.class);
        controller = new GithubLinkController(jwtProvider, userRepository, repoMemberService);
        ReflectionTestUtils.setField(controller, "clientId", "test-client");
        ReflectionTestUtils.setField(controller, "clientSecret", "test-secret");
        ReflectionTestUtils.setField(controller, "redirectUri", "https://back.test/api/users/me/github/callback");
        ReflectionTestUtils.setField(controller, "frontendUrl", "https://front.test");
    }

    private User demoAdmin() {
        User user = User.builder().email(null).password(null).nickname("체험 관리자").build();
        user.linkGithub("cogi-demo:admin", "demo.cogi-admin", null);
        ReflectionTestUtils.setField(user, "id", DEMO_ADMIN_ID);
        return user;
    }

    @Test
    void 테스트_계정은_콜백에서_GitHub_이_붙지_않고_안내로_돌려보낸다() throws Exception {
        User user = demoAdmin();
        when(userRepository.findById(DEMO_ADMIN_ID)).thenReturn(Optional.of(user));
        String state = jwtProvider.createGithubLinkState(DEMO_ADMIN_ID);
        MockHttpServletResponse res = new MockHttpServletResponse();

        controller.callback("any-code", state, res);

        assertThat(res.getRedirectedUrl()).isEqualTo("https://front.test/app/my?error=demo_account");
        assertThat(user.getGithubId()).isEqualTo("cogi-demo:admin");
        verify(userRepository, never()).save(any());
        verifyNoInteractions(repoMemberService);
    }
}
