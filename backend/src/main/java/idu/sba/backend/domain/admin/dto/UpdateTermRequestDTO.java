package idu.sba.backend.domain.admin.dto;

// PUT /api/admin/terms/{termId} — 프론트가 { content } 로 보냄
public record UpdateTermRequestDTO(String content) {}
