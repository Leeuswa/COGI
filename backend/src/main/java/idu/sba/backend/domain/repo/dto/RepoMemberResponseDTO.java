package idu.sba.backend.domain.repo.dto;

import idu.sba.backend.domain.repo.entity.RepoMember;
import idu.sba.backend.domain.user.entity.User;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class RepoMemberResponseDTO {

    private final Long userId;
    private final String githubUsername;
    private final String role;
    private final LocalDateTime joinedAt;

    private RepoMemberResponseDTO(Long userId, String githubUsername, String role, LocalDateTime joinedAt){
        this.userId = userId;
        this.githubUsername = githubUsername;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public static RepoMemberResponseDTO of(RepoMember member, User user){
        return new RepoMemberResponseDTO(
                member.getUserId(), user.getGithubUsername(), member.getRole().name(), member.getJoinedAt());
    }

}
