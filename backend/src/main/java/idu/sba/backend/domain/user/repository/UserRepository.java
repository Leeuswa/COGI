package idu.sba.backend.domain.user.repository;

import idu.sba.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Long> {

    //가입시 이메일 체크
    boolean existsByEmail(String  email);

    //로그인시 이메일 체크
    Optional<User> findAllByEmail(String email);

}
