package idu.sba.backend.domain.admin.dto;

import idu.sba.backend.domain.user.entity.Role;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.entity.UserStatus;

// 관리자 회원관리 목록 한 행.
public record AdminMemberResponseDTO(
        Long id,
        String email,
        String planName,   // planId → Plan.name 매핑 결과 (null이면 "FREE")
        Role role,         // Jackson이 이름 문자열("USER"/"ADMIN")로 직렬화
        UserStatus status  // "ACTIVE"/"SUSPENDED"
) {
    public static AdminMemberResponseDTO of(User user, String planName) {
        return new AdminMemberResponseDTO(user.getId(), user.getEmail(), planName, user.getRole(), user.getStatus());
    }
}
