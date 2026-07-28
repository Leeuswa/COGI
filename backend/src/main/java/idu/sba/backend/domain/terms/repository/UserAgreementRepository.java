package idu.sba.backend.domain.terms.repository;

import idu.sba.backend.domain.terms.entity.UserAgreement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAgreementRepository extends JpaRepository<UserAgreement,Long> {

    // 이 사용자가 동의한 약관들 (마이페이지 동의 현황용)
    List<UserAgreement> findByUserIdAndAgreedTrue(Long userId);

    // 재동의/철회 갱신용 — (사용자, 약관) 대표 1행. 과거 중복 데이터가 남아 있어도 NonUnique 예외 없이 최초 행을 쓴다.
    Optional<UserAgreement> findFirstByUserIdAndTermIdOrderByIdAsc(Long userId, Long termId);
}
