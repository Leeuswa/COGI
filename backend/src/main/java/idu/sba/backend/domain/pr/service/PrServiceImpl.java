package idu.sba.backend.domain.pr.service;

import idu.sba.backend.domain.pr.dto.PrDetailResponseDTO;
import idu.sba.backend.domain.pr.dto.PrFileResponseDTO;
import idu.sba.backend.domain.pr.dto.PrListItemResponseDTO;
import idu.sba.backend.domain.pr.dto.PrReviewHistoryItemDTO;
import idu.sba.backend.domain.pr.dto.PrReviewResponseDTO;
import idu.sba.backend.domain.pr.dto.PrSummaryResponseDTO;
import idu.sba.backend.domain.pr.entity.PullRequest;
import idu.sba.backend.domain.pr.repository.PullRequestRepository;
import idu.sba.backend.domain.repo.client.GithubApiClient;
import idu.sba.backend.domain.repo.entity.GithubRepository;
import idu.sba.backend.domain.repo.entity.RepoMember;
import idu.sba.backend.domain.repo.repository.GithubRepositoryRepository;
import idu.sba.backend.domain.repo.repository.RepoMemberRepository;
import idu.sba.backend.domain.review.dto.ReviewIssueResponseDTO;
import idu.sba.backend.domain.review.dto.ReviewQuestionResponseDTO;
import idu.sba.backend.domain.review.entity.IssueSeverity;
import idu.sba.backend.domain.review.entity.Review;
import idu.sba.backend.domain.review.entity.ReviewIssue;
import idu.sba.backend.domain.review.repository.ReviewIssueRepository;
import idu.sba.backend.domain.review.repository.ReviewQuestionRepository;
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
    private final ReviewQuestionRepository reviewQuestionRepository;
    private final UserRepository userRepository;
    private final GithubApiClient githubApiClient;

    @Override
    public PrReviewResponseDTO getPrReview(Long currentUserId, Long prId) {
        PullRequest pr = pullRequestRepository.findById(prId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PR_NOT_FOUND));
        GithubRepository repo = githubRepositoryRepository.findById(pr.getRepoId())
                .orElseThrow(() -> new BusinessException(ErrorCode.REPO_NOT_FOUND));

        //이 레포의 팀원만 조회 가능 — role도 같이 가져와서 myRole(승인 버튼 노출 여부)에 씀
        RepoMember member = repoMemberRepository.findByRepoIdAndUserId(repo.getId(), currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_REPO_MEMBER));

        Long latestReviewId = reviewRepository.findTopByPrIdOrderByCreatedAtDesc(prId).map(Review::getId).orElse(null);
        List<ReviewIssueResponseDTO> issues = latestReviewId == null ? List.of() //아직 리뷰가 없으면(PR이 OPEN) 빈 목록 — 예외 아님
                : reviewIssueRepository.findByReviewId(latestReviewId).stream().map(ReviewIssueResponseDTO::of).toList();
        // 후속 질문 기록(2026-07-29) — 최신 리뷰(위 issues와 동일 리뷰) 기준, 시간순
        List<ReviewQuestionResponseDTO> questions = latestReviewId == null ? List.of()
                : reviewQuestionRepository.findByReviewIdOrderByCreatedAtAsc(latestReviewId).stream()
                        .map(ReviewQuestionResponseDTO::of).toList();

        List<PrReviewHistoryItemDTO> reviewHistory = reviewRepository.findByPrIdOrderByCreatedAtDesc(prId).stream()
                .map(this::toHistoryItem)
                .toList();

        return PrReviewResponseDTO.of(PrDetailResponseDTO.of(pr, resolveAuthorName(pr), member.getRole().name()),
                issues, reviewHistory, questions);
    }

    //재검토로 이슈가 안 나오게 된 이력까지 완전히 추적하진 않지만(설계 추론, [간단 버전]),
    //리뷰가 몇 번 돌았고 CRITICAL이 몇 건에서 몇 건으로 줄었는지는 이걸로 보인다
    private PrReviewHistoryItemDTO toHistoryItem(Review review) {
        List<ReviewIssue> issues = reviewIssueRepository.findByReviewId(review.getId());
        long criticalCount = issues.stream().filter(i -> i.getSeverity() == IssueSeverity.CRITICAL).count();
        return PrReviewHistoryItemDTO.of(review.getId(), review.getCreatedAt(), review.getModelName(),
                review.getStatus().name(), issues.size(), (int) criticalCount);
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
    public List<PrListItemResponseDTO> listReviewedPrs(Long currentUserId, Long repoId, String severity, String author) {
        requireRepoMember(currentUserId, repoId);
        return pullRequestRepository.findByRepoIdOrderByCreatedAtDesc(repoId).stream()
                .map(pr -> {
                    List<ReviewIssue> issues = reviewRepository.findTopByPrIdOrderByCreatedAtDesc(pr.getId())
                            .map(Review::getId)
                            .map(reviewIssueRepository::findByReviewId)
                            .orElse(List.of());
                    return PrListItemResponseDTO.of(pr, resolveAuthorName(pr), issues);
                })
                // topSeverity/authorName은 DB 컬럼이 아니라 이슈 목록·닉네임 조회로 만들어지는 값이라
                // DB 쿼리로 못 거르고, 응답 DTO를 만든 뒤 여기서 거른다(FR-41/42, 프론트가 하던 걸 서버로 이동)
                .filter(item -> severity == null || severity.isBlank() || severity.equals(item.getTopSeverity()))
                .filter(item -> author == null || author.isBlank() || author.equals(item.getAuthorName()))
                .toList();
    }

    // authorId가 COGI 가입자를 가리키면 닉네임을, 아니면(미가입자·해지 등) GitHub 로그인명 원본으로 폴백
    private String resolveAuthorName(PullRequest pr) {
        if (pr.getAuthorId() != null) {
            return userRepository.findById(pr.getAuthorId()).map(User::getNickname).orElse(pr.getAuthorLogin());
        }
        return pr.getAuthorLogin();
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
