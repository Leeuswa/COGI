package idu.sba.backend.domain.repo.repository;

import idu.sba.backend.domain.repo.entity.GithubRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GithubRepositoryRepository extends JpaRepository<GithubRepository, Long> {
}
