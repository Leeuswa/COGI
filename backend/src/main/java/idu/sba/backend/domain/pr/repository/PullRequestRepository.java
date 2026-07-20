package idu.sba.backend.domain.pr.repository;

import idu.sba.backend.domain.pr.entity.PullRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PullRequestRepository extends JpaRepository<PullRequest, Long> {

    //웹훅 upsert 키 — PR은 레포+GitHub PR 번호 조합으로 유일
    Optional<PullRequest> findByRepoIdAndGithubPrNumber(Long repoId, Integer githubPrNumber);

    //PR 리뷰 대시보드(API-032, 팀 대신 레포 기준) [설계 추론]: 레포의 PR 목록(최신순)
    List<PullRequest> findByRepoIdOrderByCreatedAtDesc(Long repoId);

}
