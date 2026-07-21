package idu.sba.backend.domain.review.repository;

import idu.sba.backend.domain.review.entity.ReviewIssue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewIssueRepository extends JpaRepository<ReviewIssue, Long> {
    List<ReviewIssue> findByReviewId(Long reviewId);

    //리뷰 히스토리 목록 [설계 추론]: 여러 리뷰의 이슈를 한 번에 조회해 N+1을 피한다
    List<ReviewIssue> findByReviewIdIn(List<Long> reviewIds);
}
