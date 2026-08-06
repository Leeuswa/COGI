package idu.sba.backend.domain.review.repository;

import idu.sba.backend.domain.review.entity.ReviewQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewQuestionRepository extends JpaRepository<ReviewQuestion, Long> {

    List<ReviewQuestion> findByReviewIdOrderByCreatedAtAsc(Long reviewId);
}
