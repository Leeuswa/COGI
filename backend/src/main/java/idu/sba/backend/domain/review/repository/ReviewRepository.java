package idu.sba.backend.domain.review.repository;

import idu.sba.backend.domain.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    //API-025: PR에 대한 리뷰 결과 조회(아직 없으면 empty — PR이 OPEN 상태)
    Optional<Review> findByPrId(Long prId);

}
