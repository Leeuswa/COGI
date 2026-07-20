package idu.sba.backend.domain.pr.service;

import idu.sba.backend.domain.pr.dto.PrDetailResponseDTO;
import idu.sba.backend.domain.pr.dto.PrFileResponseDTO;
import idu.sba.backend.domain.pr.dto.PrListItemResponseDTO;
import idu.sba.backend.domain.pr.dto.PrReviewResponseDTO;
import idu.sba.backend.domain.pr.dto.PrSummaryResponseDTO;
import idu.sba.backend.domain.pr.entity.PullRequest;
import idu.sba.backend.domain.pr.repository.PullRequestRepository;
import idu.sba.backend.domain.repo.client.GithubApiClient;
import idu.sba.backend.domain.repo.entity.GithubRepository;
import idu.sba.backend.domain.repo.repository.GithubRepositoryRepository;
import idu.sba.backend.domain.repo.repository.RepoMemberRepository;
import idu.sba.backend.domain.review.dto.ReviewIssueResponseDTO;
import idu.sba.backend.domain.review.entity.Review;
import idu.sba.backend.domain.review.entity.ReviewIssue;
import idu.sba.backend.domain.review.repository.ReviewIssueRepository;
import idu.sba.backend.domain.review.repository.ReviewRepository;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.repository.UserRepository;
import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PrServiceImpl implements PrService {

    private final PullRequestRepository pullRequestRepository;
    private final GithubRepositoryRepository githubRepositoryRepository;
    private final RepoMemberRepository repoMemberRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewIssueRepository reviewIssueRepository;
    private final UserRepository userRepository;
    private final GithubApiClient githubApiClient;

    @Override
    public PrReviewResponseDTO getPrReview(Long currentUserId, Long prId) {
        PullRequest pr = pullRequestRepository.findById(prId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PR_NOT_FOUND));
        GithubRepository repo = githubRepositoryRepository.findById(pr.getRepoId())
                .orElseThrow(() -> new BusinessException(ErrorCode.REPO_NOT_FOUND));

        //이 레포의 팀원만 조회 가능(RepoMemberServiceImpl의 멤버십 체크와 동일 패턴, 역할 무관)
        if (!repoMemberRepository.existsByRepoIdAndUserId(repo.getId(), currentUserId)) {
            throw new BusinessException(ErrorCode.NOT_REPO_MEMBER);
        }

        List<ReviewIssueResponseDTO> issues = reviewRepository.findByPrId(prId)
                .map(Review::getId)
                .map(reviewIssueRepository::findByReviewId)
                .map(list -> list.stream().map(ReviewIssueResponseDTO::of).toList())
                .orElse(List.of()); //아직 리뷰가 없으면(PR이 OPEN) 빈 목록 — 예외 아님

        String authorName = pr.getAuthorId() == null ? null
                : userRepository.findById(pr.getAuthorId()).map(u -> u.getNickname()).orElse(null);

        return PrReviewResponseDTO.of(PrDetailResponseDTO.of(pr, authorName), issues);
    }

    @Override
    public List<PrSummaryResponseDTO> listOpenPrs(Long currentUserId, Long repoId) {
        GithubRepository repo = requireRepoMember(currentUserId, repoId);
        String accessToken = requireAccessToken(currentUserId);
        return githubApiClient.listPullRequests(accessToken, repo.getFullName()).stream()
                .map(PrSummaryResponseDTO::of)
                .toList();
    }

    @Override
    public List<PrFileResponseDTO> listPrFiles(Long currentUserId, Long repoId, int prNumber) {
        GithubRepository repo = requireRepoMember(currentUserId, repoId);
        String accessToken = requireAccessToken(currentUserId);
        return githubApiClient.listPrFiles(accessToken, repo.getFullName(), prNumber).stream()
                .filter(f -> f.getPatch() != null) //바이너리/과대용량 diff는 patch가 없어 GitHub가 생략함
                .map(PrFileResponseDTO::of)
                .toList();
    }

    @Override
    public List<PrListItemResponseDTO> listReviewedPrs(Long currentUserId, Long repoId) {
        requireRepoMember(currentUserId, repoId);
        return pullRequestRepository.findByRepoIdOrderByCreatedAtDesc(repoId).stream()
                .map(pr -> {
                    String authorName = pr.getAuthorId() == null ? null
                            : userRepository.findById(pr.getAuthorId()).map(User::getNickname).orElse(null);
                    List<ReviewIssue> issues = reviewRepository.findByPrId(pr.getId())
                            .map(Review::getId)
                            .map(reviewIssueRepository::findByReviewId)
                            .orElse(List.of());
                    return PrListItemResponseDTO.of(pr, authorName, issues);
                })
                .toList();
    }

    // listOpenPrs/listPrFiles 공용 — 이 두 API는 웹훅 경로와 달리 실제 로그인 사용자가 호출하므로
    // 항상 그 사용자 본인의 GitHub 토큰을 쓴다(레포 팀장 기준이 아님)
    private GithubRepository requireRepoMember(Long currentUserId, Long repoId) {
        GithubRepository repo = githubRepositoryRepository.findById(repoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPO_NOT_FOUND));
        if (!repoMemberRepository.existsByRepoIdAndUserId(repoId, currentUserId)) {
            throw new BusinessException(ErrorCode.NOT_REPO_MEMBER);
        }
        return repo;
    }

    private String requireAccessToken(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.getGithubAccessToken() == null) {
            throw new BusinessException(ErrorCode.GITHUB_NOT_LINKED);
        }
        return user.getGithubAccessToken();
    }

}
