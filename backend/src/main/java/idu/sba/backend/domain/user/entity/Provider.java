package idu.sba.backend.domain.user.entity;

public enum Provider {
    LOCAL, // 이메일 + 비밀번호 가입
    GITHUB, // GitHub 소셜 로그인
    KAKAO // 카카오 소셜 로그인
}
