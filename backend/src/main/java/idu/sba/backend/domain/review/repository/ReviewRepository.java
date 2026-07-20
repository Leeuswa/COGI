package idu.sba.backend.domain.review.repository;

import idu.sba.backend.domain.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    //API-025: PR에 대한 리뷰 결과 조회(아직 없으면 empty — PR이 OPEN 상태)
    Optional<Review> findByPrId(Long prId);

    //리뷰 히스토리 [설계 추론]: 로그인 사용자의 개별 리뷰 목록(최신순)
    List<Review> findByUserIdOrderByCreatedAtDesc(Long userId);

}
