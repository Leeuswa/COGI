package idu.sba.backend.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;

// POST /api/admin/notices/email — 프론트가 { subject, content } 로 보냄
public record SendNoticeRequestDTO(
        @NotBlank(message = "제목을 입력하세요.") String subject,
        @NotBlank(message = "내용을 입력하세요.") String content) {}
