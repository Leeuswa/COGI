package idu.sba.backend.domain.repo.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "repo_members")
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

}
