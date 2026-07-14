package idu.sba.backend.domain.repo.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 테이블 정의서의 repositories — GitHub 레포지토리 연동 정보.
 * 실제 GitHub 연동(OAuth·webhook 등록, API-022/023)은 별도 작업(레포 연동)의 몫이며,
 * 여기서는 repo_members/repo_invitations가 참조할 최소 스텁만 둔다.
 */
@Entity
@Table(name = "repositories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GithubRepository {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId; //연동 등록자(팀장)
    private String githubRepoId;
    private String repoName;
    private Boolean isPrivate = false;
    private String webhookId; //nullable

    @Column(updatable = false)
    private LocalDateTime linkedAt;

    @PrePersist
    protected void onCreate(){
        this.linkedAt = LocalDateTime.now();
    }

}
