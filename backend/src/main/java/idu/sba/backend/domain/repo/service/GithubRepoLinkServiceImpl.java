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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GithubRepoLinkServiceImpl implements GithubRepoLinkService {

    private final UserRepository userRepository;
    private final GithubRepositoryRepository githubRepositoryRepository;
    private final GithubApiClient githubApiClient;
    private final RepoMemberService repoMemberService;

    @Value("${app.webhook-callback-url:}")
    private String webhookCallbackUrl;

    @Value("${github.webhook.secret}")
    private String webhookSecret;

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

        GithubRepository saved;
        try {
            saved = githubRepositoryRepository.save(
                    GithubRepository.link(currentUserId, String.valueOf(repo.getId()), repo.getName(),
                            repo.isPrivate(), repo.getFullName()));
        } catch (DataIntegrityViolationException e) {
            //위 existsBy 체크와 이 save 사이의 더블클릭 경합 — DB 유니크 제약이 막아준 것을 500 대신
            //"이미 연동됨" 응답으로 흡수
            throw new BusinessException(ErrorCode.REPO_ALREADY_LINKED);
        }

        //연동한 사람을 이 레포의 OWNER로 등록(레포 초대 기능과 연결되는 지점)
        repoMemberService.registerOwner(saved.getId(), currentUserId);

        registerWebhookIfConfigured(accessToken, saved);

        return RepoLinkResponseDTO.of(saved);
    }

    // 공개 콜백 URL이 설정된 경우(app.webhook-callback-url)에만 웹훅을 자동 등록한다 — 로컬 개발처럼
    // 공개 URL이 없는 환경에서는 조용히 건너뛰고 기존처럼 webhookId=null로 남긴다(관리자 수동 등록 경로 유지).
    // 실패해도 레포 연동 자체는 이미 끝난 뒤라 예외를 던지지 않고 로그만 남긴다(연동을 막을 이유가 아님).
    private void registerWebhookIfConfigured(String accessToken, GithubRepository repo) {
        if (webhookCallbackUrl == null || webhookCallbackUrl.isBlank()) {
            log.info("app.webhook-callback-url 미설정 — 웹훅 자동 등록 건너뜀(레포 관리자가 수동 등록 필요) - repoId={}", repo.getId());
            return;
        }
        try {
            Long webhookId = githubApiClient.createWebhook(
                    accessToken, repo.getFullName(), webhookCallbackUrl + "/api/webhooks/github", webhookSecret);
            repo.assignWebhookId(String.valueOf(webhookId));
        } catch (BusinessException e) {
            log.warn("웹훅 자동 등록 실패 — 레포 연동은 그대로 유지, 관리자가 수동 등록 필요 - repoId={}", repo.getId(), e);
        }
    }

    private String requireGithubAccessToken(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        if (user.getGithubAccessToken() == null) {
            throw new BusinessException(ErrorCode.GITHUB_NOT_LINKED);
        }
        return user.getGithubAccessToken();
    }

}
