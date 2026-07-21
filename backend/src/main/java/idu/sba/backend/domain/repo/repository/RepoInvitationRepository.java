package idu.sba.backend.domain.repo.repository;

import idu.sba.backend.domain.repo.entity.RepoInvitation;
import idu.sba.backend.domain.repo.entity.RepoInvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RepoInvitationRepository extends JpaRepository<RepoInvitation, Long> {

    //내 초대 목록(인앱 알림)
    List<RepoInvitation> findByInvitedUserIdAndStatus(Long invitedUserId, RepoInvitationStatus status);

    //이메일 초대 링크 클릭 시 토큰으로 조회(케이스②·③, 비로그인 상태에서 호출됨)
    Optional<RepoInvitation> findByInviteToken(String inviteToken);

    //GitHub 가입 완료 시 자동 매칭 대상 조회(케이스① 유형 재확인 및 케이스③ 신규가입)
    List<RepoInvitation> findByStatusAndInvitedUserIdIsNullAndInvitedGithubUsernameIgnoreCase(
            RepoInvitationStatus status, String invitedGithubUsername);

    //이메일 초대(케이스②·③) 자동 매칭 대상 조회
    List<RepoInvitation> findByStatusAndInvitedUserIdIsNullAndInviteeEmailIgnoreCase(
            RepoInvitationStatus status, String inviteeEmail);

    //같은 레포에 같은 사람한테 이미 보낸 PENDING 초대가 있는지(중복 발송 방지)
    boolean existsByRepoIdAndInvitedGithubUsernameIgnoreCaseAndStatus(
            Long repoId, String invitedGithubUsername, RepoInvitationStatus status);

}
