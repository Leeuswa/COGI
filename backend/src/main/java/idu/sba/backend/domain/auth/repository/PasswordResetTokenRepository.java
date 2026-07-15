package idu.sba.backend.domain.auth.repository;

import idu.sba.backend.domain.auth.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken,Long> {

    //재설정 토큰으로 조회
    Optional<PasswordResetToken> findByResetToken(String resetToken);
}
