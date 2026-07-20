package idu.sba.backend.domain.pr.repository;

import idu.sba.backend.domain.pr.entity.PullRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PullRequestRepository extends JpaRepository<PullRequest, Long> {

    //웹훅 upsert 키 — PR은 레포+GitHub PR 번호 조합으로 유일
    Optional<PullRequest> findByRepoIdAndGithubPrNumber(Long repoId, Integer githubPrNumber);

}
