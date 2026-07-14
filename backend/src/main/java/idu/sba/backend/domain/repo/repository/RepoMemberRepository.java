package idu.sba.backend.domain.repo.repository;

import idu.sba.backend.domain.repo.entity.RepoMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RepoMemberRepository extends JpaRepository<RepoMember, Long> {

    Optional<RepoMember> findByRepoIdAndUserId(Long repoId, Long userId);

    boolean existsByRepoIdAndUserId(Long repoId, Long userId);

}
