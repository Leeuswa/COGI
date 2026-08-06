package idu.sba.backend.domain.retention.repository;

import idu.sba.backend.domain.retention.entity.UserStreak;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserStreakRepository extends JpaRepository<UserStreak, Long> {

    Optional<UserStreak> findByUserId(Long userId);
}
