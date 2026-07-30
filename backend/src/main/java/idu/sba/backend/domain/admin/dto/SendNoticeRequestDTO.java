package idu.sba.backend.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;

// POST /api/admin/notices/email — 프론트가 { subject, content, urgent } 로 보냄
// urgent=true(긴급공지)일 때만 이메일까지 발송, 아니면 인앱 알림만. 값 없으면 false(일반).
public record SendNoticeRequestDTO(
        @NotBlank(message = "제목을 입력하세요.") String subject,
        @NotBlank(message = "내용을 입력하세요.") String content,
        boolean urgent) {}
