package idu.sba.backend.domain.terms.repository;

import idu.sba.backend.domain.terms.entity.UserAgreement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserAgreementRepository extends JpaRepository<UserAgreement,Long> {

    // 이 사용자가 동의한 약관들 (마이페이지 동의 현황용)
    List<UserAgreement> findByUserIdAndAgreedTrue(Long userId);
}
