package idu.sba.backend.domain.repo.service;

import idu.sba.backend.domain.repo.client.GithubApiClient;
import idu.sba.backend.domain.repo.client.GithubRepoDto;
import idu.sba.backend.domain.repo.repository.GithubRepositoryRepository;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.repository.UserRepository;
import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GithubRepoLinkServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private GithubRepositoryRepository githubRepositoryRepository;
    @Mock private GithubApiClient githubApiClient;
    @Mock private RepoMemberService repoMemberService;

    @InjectMocks
    private GithubRepoLinkServiceImpl service;

    private static final Long USER_ID = 1L;

    private User userWithToken(String token) {
        User user = User.createByGithub("gh-1", "user-gh", "user@test.com", token);
        setField(user, "id", USER_ID);
        return user;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private GithubRepoDto repoDto(Long id, String name, boolean isPrivate) {
        GithubRepoDto dto = new GithubRepoDto();
        setField(dto, "id", id);
        setField(dto, "name", name);
        setField(dto, "isPrivate", isPrivate);
        return dto;
    }

    @Test
    void GitHub_토큰이_없으면_레포목록조회시_GITHUB_NOT_LINKED() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithToken(null)));

        assertThatThrownBy(() -> service.listMyGithubRepos(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.GITHUB_NOT_LINKED);

        verify(githubApiClient, never()).listRepos(any());
    }

    @Test
    void 레포목록조회는_GithubApiClient_결과를_DTO로_변환한다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithToken("token-abc")));
        when(githubApiClient.listRepos("token-abc")).thenReturn(List.of(
                repoDto(111L, "repo-a", false),
                repoDto(222L, "repo-b", true)));

        var result = service.listMyGithubRepos(USER_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getGithubRepoId()).isEqualTo("111");
        assertThat(result.get(1).isPrivate()).isTrue();
    }

    @Test
    void 이미_연동된_레포는_REPO_ALREADY_LINKED() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithToken("token-abc")));
        when(githubRepositoryRepository.existsByGithubRepoId("111")).thenReturn(true);

        assertThatThrownBy(() -> service.linkRepo(USER_ID, "111"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REPO_ALREADY_LINKED);

        verify(githubApiClient, never()).getRepo(any(), any());
    }

    @Test
    void 정상_연동시_저장하고_OWNER로_등록한다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithToken("token-abc")));
        when(githubRepositoryRepository.existsByGithubRepoId("111")).thenReturn(false);
        when(githubApiClient.getRepo("token-abc", "111")).thenReturn(repoDto(111L, "repo-a", false));
        when(githubRepositoryRepository.save(any())).thenAnswer(inv -> {
            var repo = inv.getArgument(0, idu.sba.backend.domain.repo.entity.GithubRepository.class);
            setField(repo, "id", 999L);
            return repo;
        });

        var result = service.linkRepo(USER_ID, "111");

        assertThat(result.getRepoId()).isEqualTo(999L);
        assertThat(result.getRepoName()).isEqualTo("repo-a");
        assertThat(result.getWebhookId()).isNull();
        verify(repoMemberService).registerOwner(999L, USER_ID);
    }

}
