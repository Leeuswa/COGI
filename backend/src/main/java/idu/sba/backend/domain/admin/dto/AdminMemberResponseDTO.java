package idu.sba.backend.domain.admin.dto;

import idu.sba.backend.domain.user.entity.Provider;
import idu.sba.backend.domain.user.entity.Role;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.entity.UserStatus;

import java.time.LocalDateTime;

// 관리자 회원관리 목록 한 행.
public record AdminMemberResponseDTO(
        Long id,
        String email,         // 소셜 가입은 이메일 제공 동의를 안 하면 null — 화면은 nickname으로 대체 표기
        String nickname,
        Provider provider,    // LOCAL/GITHUB/KAKAO — 소셜 회원도 같은 목록에서 구분해 보려고 내려준다
        String planName,      // planId → Plan.name 매핑 결과 (null이면 "FREE")
        Role role,            // Jackson이 이름 문자열("USER"/"ADMIN")로 직렬화
        UserStatus status,    // "ACTIVE"/"SUSPENDED"/"WITHDRAWN"
        LocalDateTime createdAt // 가입일 — ISO 문자열로 직렬화
) {
    public static AdminMemberResponseDTO of(User user, String planName) {
        return new AdminMemberResponseDTO(user.getId(), user.getEmail(), user.getNickname(),
                user.getProvider(), planName,
                user.getRole(), user.getStatus(), user.getCreatedAt());
    }
}
