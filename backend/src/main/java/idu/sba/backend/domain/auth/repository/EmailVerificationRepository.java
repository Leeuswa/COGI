package idu.sba.backend.domain.auth.repository;

import idu.sba.backend.domain.auth.entity.EmailVerification;
import idu.sba.backend.domain.auth.entity.Purpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification,Long> {

    // 가장 최근 발송된 인증코드 1건 조화
    Optional<EmailVerification> findFirstByEmailOrderByCreateAtDesc(String email);

    //가입시 이메일 인증 햇는지 확인
    boolean existsByEmailAndPurposeAndVerifiedTrue(String email, Purpose purpose);
}
