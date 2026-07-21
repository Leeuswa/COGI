package idu.sba.backend.domain.admin.controller;

import idu.sba.backend.domain.admin.dto.AdminMemberResponseDTO;
import idu.sba.backend.domain.admin.dto.ChangeRoleRequestDTO;
import idu.sba.backend.domain.admin.dto.ChangeStatusRequestDTO;
import idu.sba.backend.domain.admin.service.AdminMemberService;
import idu.sba.backend.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 관리자 회원관리 . ADMIN 권한은 SecurityConfig의 /api/admin/** 규칙이 막는다.
@RestController
@RequestMapping("/api/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {

    private final AdminMemberService adminMemberService;

    //회원 목록
    @GetMapping
    public ApiResponse<List<AdminMemberResponseDTO>> getMembers() {
        return ApiResponse.ok(adminMemberService.getMembers());
    }

    // 태 변경(ACTIVE/SUSPENDED, 즉시 반영)
    @PatchMapping("/{userId}/status")
    public ApiResponse<Void> changeStatus( @AuthenticationPrincipal Long adminId,
                                            @PathVariable Long userId,
                                          @Valid @RequestBody ChangeStatusRequestDTO request) {
        adminMemberService.changeStatus(userId, adminId , request.status());
        return ApiResponse.ok("상태가 변경되었습니다.");
    }

    //권한 변경(USER/ADMIN, 즉시 반영)
    @PatchMapping("/{userId}/role")
    public ApiResponse<Void> changeRole(@AuthenticationPrincipal  Long adminId , @PathVariable Long userId,
                                        @Valid @RequestBody ChangeRoleRequestDTO request) {
        adminMemberService.changeRole(userId, adminId , request.role());
        return ApiResponse.ok("권한이 변경되었습니다.");
    }
}
