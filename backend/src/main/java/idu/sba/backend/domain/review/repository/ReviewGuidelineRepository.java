package idu.sba.backend.domain.review.repository;

import idu.sba.backend.domain.review.entity.ReviewGuideline;
import idu.sba.backend.domain.user.entity.Level;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReviewGuidelineRepository extends JpaRepository<ReviewGuideline, Long> {
    Optional<ReviewGuideline> findByLevel(Level level);
}
