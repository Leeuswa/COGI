package idu.sba.backend.domain.user.repository;

import idu.sba.backend.domain.user.dto.TotpSetupResponseDTO;
import idu.sba.backend.domain.user.entity.Provider;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Long> {

    //가입시 이메일 체크
    boolean existsByEmail(String  email);
    // 로그인/비번재설정 조회용
    Optional<User> findByProviderAndEmail(Provider provider, String email);
    //github 아이디 여부 확인
    Optional<User> findByGithubId(String githubId);
    //카카오 아이디 여부 확인
    Optional<User> findByKakaoId(String kakaoId);
    //레포 초대 시 GitHub 아이디로 대상 사용자 조회
    Optional<User> findByGithubUsername(String githubUsername);

    // 탈퇴 보관기간 만료분 정리용. 탈퇴 시점 컬럼을 따로 두지 않고 updatedAt을 쓰는 이유:
    // withdraw()가 엔티티를 수정하면서 @PreUpdate로 updatedAt이 그 시각으로 찍히고,
    // WITHDRAWN 회원은 이후 어떤 경로로도 수정되지 않는다(로그인·관리자 액션 모두 차단).
    // 기존에 이미 탈퇴한 회원도 그대로 대상이 된다.
    long deleteByStatusAndUpdatedAtBefore(UserStatus status, LocalDateTime threshold);





}
