package idu.sba.backend.domain.retention.repository;

import idu.sba.backend.domain.retention.entity.PetState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PetStateRepository extends JpaRepository<PetState, Long> {

    // 사용자당 1행이라 단건 조회 (없으면 첫 조회 때 만든다)
    Optional<PetState> findByUserId(Long userId);
}
