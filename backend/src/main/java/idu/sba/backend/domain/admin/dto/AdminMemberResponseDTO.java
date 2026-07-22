package idu.sba.backend.domain.admin.dto;

import idu.sba.backend.domain.user.entity.Role;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.entity.UserStatus;

import java.time.LocalDateTime;

// 관리자 회원관리 목록 한 행.
public record AdminMemberResponseDTO(
        Long id,
        String email,
        String planName,      // planId → Plan.name 매핑 결과 (null이면 "FREE")
        Role role,            // Jackson이 이름 문자열("USER"/"ADMIN")로 직렬화
        UserStatus status,    // "ACTIVE"/"SUSPENDED"
        LocalDateTime createdAt // 가입일 — ISO 문자열로 직렬화
) {
    public static AdminMemberResponseDTO of(User user, String planName) {
        return new AdminMemberResponseDTO(user.getId(), user.getEmail(), planName,
                user.getRole(), user.getStatus(), user.getCreatedAt());
    }
}
