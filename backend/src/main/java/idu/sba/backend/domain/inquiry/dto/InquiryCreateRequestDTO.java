package idu.sba.backend.domain.inquiry.dto;

import jakarta.validation.constraints.NotBlank;

public record InquiryCreateRequestDTO(
        @NotBlank String title,
        @NotBlank String content
) {}
