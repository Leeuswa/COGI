package idu.sba.backend.domain.review.service;

import idu.sba.backend.domain.notification.service.NotificationService;
import idu.sba.backend.domain.pr.entity.PullRequest;
import idu.sba.backend.domain.pr.repository.PullRequestRepository;
import idu.sba.backend.domain.repo.entity.GithubRepository;
import idu.sba.backend.domain.repo.entity.RepoMember;
import idu.sba.backend.domain.repo.entity.RepoRole;
import idu.sba.backend.domain.repo.repository.GithubRepositoryRepository;
import idu.sba.backend.domain.repo.repository.RepoMemberRepository;
import idu.sba.backend.domain.review.entity.IssueSeverity;
import idu.sba.backend.domain.review.entity.IssueStatus;
import idu.sba.backend.domain.review.entity.Review;
import idu.sba.backend.domain.review.entity.ReviewIssue;
import idu.sba.backend.domain.review.entity.ReviewTargetType;
import idu.sba.backend.domain.review.repository.ReviewIssueRepository;
import idu.sba.backend.domain.review.repository.ReviewRepository;
import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewIssueServiceImpl implements ReviewIssueService {

    private final ReviewIssueRepository reviewIssueRepository;
    private final ReviewRepository reviewRepository;
    private final PullRequestRepository pullRequestRepository;
    private final RepoMemberRepository repoMemberRepository;
    private final GithubRepositoryRepository githubRepositoryRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public void finalizeIssue(Long currentUserId, Long issueId, String verdict) {
        ReviewIssue issue = reviewIssueRepository.findById(issueId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ISSUE_NOT_FOUND));
        Review review = reviewRepository.findById(issue.getReviewId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ISSUE_NOT_FOUND)); // 이슈만 있고 리뷰가 없는 건 데이터 정합성 문제라 같은 코드로 취급

        if (!canAccessReview(currentUserId, review)) {
            throw new BusinessException(ErrorCode.ISSUE_ACCESS_DENIED);
        }

        IssueStatus verdictStatus = parseVerdict(verdict);
        // PR 리뷰의 CRITICAL 이슈는 RESOLVED/IGNORED 둘 다 팀장 승인을 거친다(AI 오판을 "무시함"으로
        // 검증 없이 넘기지 못하게) — 그 외(붙여넣기/업로드, 낮은 심각도)는 바로 반영
        if (review.getTargetType() == ReviewTargetType.PR && issue.getSeverity() == IssueSeverity.CRITICAL) {
            if (verdictStatus == IssueStatus.RESOLVED) {
                issue.requestResolve();
            } else {
                issue.requestIgnore();
            }
            notifyOwnerOfPendingRequest(review, verdictStatus);
        } else {
            issue.finalizeAs(verdictStatus);
        }
    }

    // PR 리뷰(웹훅 경로)는 review.userId가 항상 레포 팀장으로 저장돼 있어(createFromPr) 본인 일치 검사로는
    // 팀장이 아닌 멤버가 자기 PR 이슈도 영영 처리할 수 없었음(FR-37: 멤버도 이슈 해결 요청 권한이 있어야 함) —
    // PR에 연결된 리뷰는 "본인 리뷰인지"가 아니라 "그 레포 멤버인지"로 판단
    private boolean canAccessReview(Long userId, Review review) {
        if (review.getPrId() != null) {
            PullRequest pr = pullRequestRepository.findById(review.getPrId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PR_NOT_FOUND));
            return repoMemberRepository.existsByRepoIdAndUserId(pr.getRepoId(), userId);
        }
        return userId.equals(review.getUserId());
    }

    // 승인 대기가 생겼을 때 레포 팀장에게 인앱 알림 — PENDING은 항상 PR 리뷰 경로에서만 생겨서 prId가 항상 있음
    private void notifyOwnerOfPendingRequest(Review review, IssueStatus verdictStatus) {
        PullRequest pr = pullRequestRepository.findById(review.getPrId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PR_NOT_FOUND));
        GithubRepository repo = githubRepositoryRepository.findById(pr.getRepoId())
                .orElseThrow(() -> new BusinessException(ErrorCode.REPO_NOT_FOUND));

        String verdictLabel = verdictStatus == IssueStatus.RESOLVED ? "해결됨" : "무시함";
        notificationService.broadcast(
                List.of(repo.getUserId()),
                "⏳",
                "이슈 승인 대기",
                "PR #%d의 CRITICAL 이슈를 '%s'(으)로 판정 요청했습니다 — 승인이 필요해요.".formatted(pr.getGithubPrNumber(), verdictLabel),
                "/app/prs/%d".formatted(pr.getId())
        );
    }

    @Override
    @Transactional
    public void decideResolveRequest(Long approverId, Long issueId, boolean approve) {
        ReviewIssue issue = reviewIssueRepository.findById(issueId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ISSUE_NOT_FOUND));
        if (issue.getStatus() != IssueStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_INPUT); // 승인 대기 중인 이슈만 결정 가능
        }
        Review review = reviewRepository.findById(issue.getReviewId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ISSUE_NOT_FOUND));
        PullRequest pr = pullRequestRepository.findById(review.getPrId()) // PENDING은 항상 PR 리뷰 경로에서만 생겨서 prId가 항상 있음
                .orElseThrow(() -> new BusinessException(ErrorCode.PR_NOT_FOUND));

        RepoMember member = repoMemberRepository.findByRepoIdAndUserId(pr.getRepoId(), approverId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_REPO_MEMBER));
        if (member.getRole() != RepoRole.OWNER) {
            throw new BusinessException(ErrorCode.NOT_REPO_OWNER);
        }

        if (approve) {
            issue.approve(approverId);
        } else {
            issue.reject();
        }
    }

    // 사용자가 실제로 고를 수 있는 판정은 RESOLVED/IGNORED뿐 — OPEN/PENDING은 판정이 아니라 시스템 상태값이라 여기로 못 들어옴
    private IssueStatus parseVerdict(String verdict) {
        IssueStatus status;
        try {
            status = IssueStatus.valueOf(verdict);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (status != IssueStatus.RESOLVED && status != IssueStatus.IGNORED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return status;
    }

}
