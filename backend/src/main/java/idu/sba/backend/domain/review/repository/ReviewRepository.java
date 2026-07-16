package idu.sba.backend.domain.review.repository;

import idu.sba.backend.domain.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {
}
