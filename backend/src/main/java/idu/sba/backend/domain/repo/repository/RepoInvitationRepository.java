package idu.sba.backend.domain.repo.repository;

import idu.sba.backend.domain.repo.entity.RepoInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepoInvitationRepository extends JpaRepository<RepoInvitation, Long> {
}
