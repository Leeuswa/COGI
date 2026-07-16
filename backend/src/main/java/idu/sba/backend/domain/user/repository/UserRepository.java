package idu.sba.backend.domain.user.repository;

import idu.sba.backend.domain.user.entity.Provider;
import idu.sba.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

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


}
