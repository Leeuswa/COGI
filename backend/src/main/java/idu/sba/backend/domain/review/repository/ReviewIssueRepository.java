package idu.sba.backend.domain.review.repository;

import idu.sba.backend.domain.review.entity.ReviewIssue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewIssueRepository extends JpaRepository<ReviewIssue, Long> {
    List<ReviewIssue> findByReviewId(Long reviewId);
}
