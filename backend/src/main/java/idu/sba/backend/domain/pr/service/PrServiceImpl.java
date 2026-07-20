package idu.sba.backend.domain.pr.service;

import idu.sba.backend.domain.pr.dto.PrDetailResponseDTO;
import idu.sba.backend.domain.pr.dto.PrReviewResponseDTO;
import idu.sba.backend.domain.pr.entity.PullRequest;
import idu.sba.backend.domain.pr.repository.PullRequestRepository;
import idu.sba.backend.domain.repo.entity.GithubRepository;
import idu.sba.backend.domain.repo.repository.GithubRepositoryRepository;
import idu.sba.backend.domain.repo.repository.RepoMemberRepository;
import idu.sba.backend.domain.review.dto.ReviewIssueResponseDTO;
import idu.sba.backend.domain.review.entity.Review;
import idu.sba.backend.domain.review.repository.ReviewIssueRepository;
import idu.sba.backend.domain.review.repository.ReviewRepository;
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

}
