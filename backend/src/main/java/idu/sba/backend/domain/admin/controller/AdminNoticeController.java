package idu.sba.backend.domain.admin.controller;

import idu.sba.backend.domain.admin.dto.NoticeResponseDTO;
import idu.sba.backend.domain.admin.dto.SendNoticeRequestDTO;
import idu.sba.backend.domain.admin.dto.SendNoticeResponseDTO;
import idu.sba.backend.domain.admin.service.AdminNoticeService;
import idu.sba.backend.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 관리자 전체 공지. ADMIN 권한은 SecurityConfig /api/admin/** 규칙이 막는다.
@RestController
@RequestMapping("/api/admin/notices")
@RequiredArgsConstructor
public class AdminNoticeController {

    private final AdminNoticeService adminNoticeService;

    //전체 회원 공지 메일 발송(비동기 시작) — 실제 발송 결과는 이력 목록에서 확인
    @PostMapping("/email")
    public ApiResponse<SendNoticeResponseDTO> sendNotice(@AuthenticationPrincipal Long adminId,
                                                         @Valid @RequestBody SendNoticeRequestDTO request) {
        SendNoticeResponseDTO result = adminNoticeService.sendNoticeToAll(adminId, request.subject(), request.content());
        return ApiResponse.ok("공지 발송을 시작했습니다.", result);
    }

    // 공지 발송 이력 (최신순)
    @GetMapping
    public ApiResponse<List<NoticeResponseDTO>> getNotices() {
        return ApiResponse.ok(adminNoticeService.getNotices());
    }
}
