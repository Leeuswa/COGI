package idu.sba.backend.domain.notification.controller;

import idu.sba.backend.domain.notification.dto.NotificationResponseDTO;
import idu.sba.backend.domain.notification.service.NotificationService;
import idu.sba.backend.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 인앱 알림(종 아이콘). 로그인 사용자 본인 알림만 — SecurityConfig의 anyRequest().authenticated()가 막는다.
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<List<NotificationResponseDTO>> getMyNotifications(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(notificationService.getMyNotifications(userId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> dismiss(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        notificationService.dismiss(userId, id);
        return ApiResponse.ok("알림을 삭제했습니다.");
    }
}
