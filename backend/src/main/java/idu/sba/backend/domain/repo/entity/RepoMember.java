package idu.sba.backend.domain.repo.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

//동시 요청(더블클릭, 멀티탭)으로 같은 (repo, user) 멤버 row가 두 번 생기면 findByRepoIdAndUserId 같은
//단일결과 조회가 전부 IncorrectResultSizeDataAccessException으로 깨져서 그 레포에 대한 모든 요청이
//영구히 500이 되므로, 애플리케이션의 existsBy-then-save 체크만으로는 부족해 DB 제약으로도 막는다
@Entity
@Table(name = "repo_members", uniqueConstraints = @UniqueConstraint(columnNames = {"repo_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RepoMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long repoId;
    private Long userId;

    @Enumerated(EnumType.STRING)
    private RepoRole role;

    private LocalDateTime joinedAt;

    @PrePersist
    protected void onCreate(){
        this.joinedAt = LocalDateTime.now();
    }

    private RepoMember(Long repoId, Long userId, RepoRole role){
        this.repoId = repoId;
        this.userId = userId;
        this.role = role;
    }

    public static RepoMember of(Long repoId, Long userId, RepoRole role){
        return new RepoMember(repoId, userId, role);
    }

    //팀장 위임 — 트랜잭션 안에서 호출되면 dirty checking으로 자동 반영됨(별도 save 불필요)
    public void changeRole(RepoRole role){
        this.role = role;
    }

}
