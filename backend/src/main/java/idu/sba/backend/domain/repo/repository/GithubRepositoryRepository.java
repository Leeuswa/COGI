package idu.sba.backend.domain.repo.repository;

import idu.sba.backend.domain.repo.entity.GithubRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GithubRepositoryRepository extends JpaRepository<GithubRepository, Long> {

    Optional<GithubRepository> findByGithubRepoId(String githubRepoId);

    boolean existsByGithubRepoId(String githubRepoId);

    List<GithubRepository> findByUserId(Long userId);

}
