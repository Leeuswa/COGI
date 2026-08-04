package idu.sba.backend.domain.admin.service;

import idu.sba.backend.domain.admin.dto.AdminMemberResponseDTO;
import idu.sba.backend.domain.user.entity.Role;
import idu.sba.backend.domain.user.entity.UserStatus;

import java.util.List;

// 관리자 회원관리
public interface AdminMemberService {

    List<AdminMemberResponseDTO> getMembers();

    void changeStatus(Long userId, Long adminId , UserStatus status);

    void changeRole(Long userId, Long adminId, Role role);

    // 회원 영구 삭제 — 정지(SUSPENDED) 상태인 계정만 허용
    void deleteMember(Long userId, Long adminId);
}
