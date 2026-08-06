package idu.sba.backend.domain.review.repository;

import idu.sba.backend.domain.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    //API-025: PR에 대한 리뷰 결과 조회(아직 없으면 empty — PR이 OPEN 상태)
    //PR 하나에 리뷰가 여러 개 쌓일 수 있음(웹훅 synchronize마다 새 Review row 생성) — 항상 최신 것만 조회
    Optional<Review> findTopByPrIdOrderByCreatedAtDesc(Long prId);

    //재검토로 쌓인 전체 리뷰 이력(최신순) — 이슈 화면엔 최신 리뷰만 보여주되, PR 상세엔 이력을 같이 노출
    List<Review> findByPrIdOrderByCreatedAtDesc(Long prId);

    //리뷰 히스토리 [설계 추론]: 로그인 사용자의 개별 리뷰 목록(최신순)
    List<Review> findByUserIdOrderByCreatedAtDesc(Long userId);

}
