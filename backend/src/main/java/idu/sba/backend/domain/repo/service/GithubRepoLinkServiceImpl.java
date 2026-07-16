package idu.sba.backend.domain.repo.service;

import idu.sba.backend.domain.repo.client.GithubApiClient;
import idu.sba.backend.domain.repo.client.GithubRepoDto;
import idu.sba.backend.domain.repo.dto.GithubRepoResponseDTO;
import idu.sba.backend.domain.repo.dto.RepoLinkResponseDTO;
import idu.sba.backend.domain.repo.entity.GithubRepository;
import idu.sba.backend.domain.repo.repository.GithubRepositoryRepository;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.repository.UserRepository;
import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GithubRepoLinkServiceImpl implements GithubRepoLinkService {

    private final UserRepository userRepository;
    private final GithubRepositoryRepository githubRepositoryRepository;
    private final GithubApiClient githubApiClient;
    private final RepoMemberService repoMemberService;

    @Override
    public List<GithubRepoResponseDTO> listMyGithubRepos(Long currentUserId) {
        String accessToken = requireGithubAccessToken(currentUserId);
        return githubApiClient.listRepos(accessToken).stream()
                .map(GithubRepoResponseDTO::of)
                .toList();
    }

    @Override
    @Transactional
    public RepoLinkResponseDTO linkRepo(Long currentUserId, String githubRepoId) {
        String accessToken = requireGithubAccessToken(currentUserId);

        if (githubRepositoryRepository.existsByGithubRepoId(githubRepoId)) {
            throw new BusinessException(ErrorCode.REPO_ALREADY_LINKED);
        }

        GithubRepoDto repo = githubApiClient.getRepo(accessToken, githubRepoId);

        GithubRepository saved = githubRepositoryRepository.save(
                GithubRepository.link(currentUserId, String.valueOf(repo.getId()), repo.getName(), repo.isPrivate()));

        //연동한 사람을 이 레포의 OWNER로 등록(레포 초대 기능과 연결되는 지점)
        repoMemberService.registerOwner(saved.getId(), currentUserId);

        return RepoLinkResponseDTO.of(saved);
    }

    private String requireGithubAccessToken(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        if (user.getGithubAccessToken() == null) {
            throw new BusinessException(ErrorCode.GITHUB_NOT_LINKED);
        }
        return user.getGithubAccessToken();
    }

}
