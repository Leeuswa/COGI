package idu.sba.backend.domain.admin.dto;

// PUT /api/admin/review-guidelines/{level} — 프론트가 { promptGuideline } 로 보냄
public record SaveGuidelineRequestDTO(String promptGuideline) {}
