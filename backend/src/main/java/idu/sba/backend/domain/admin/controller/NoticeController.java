package idu.sba.backend.domain.admin.controller;

import idu.sba.backend.domain.admin.dto.UserNoticeResponseDTO;
import idu.sba.backend.domain.admin.entity.NoticeStatus;
import idu.sba.backend.domain.admin.repository.NoticeRepository;
import idu.sba.backend.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 사용자 공지 조회 — 로그인 사용자면 발송 완료 공지를 마이페이지에서 본다.
// 조회 전용 한 줄이라 서비스 없이 리포지토리 직접 사용.
@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeRepository noticeRepository;

    @GetMapping
    public ApiResponse<List<UserNoticeResponseDTO>> list() {
        return ApiResponse.ok(
                noticeRepository.findByStatusOrderByCreatedAtDesc(NoticeStatus.SENT)
                        .stream().map(UserNoticeResponseDTO::of).toList());
    }
}
