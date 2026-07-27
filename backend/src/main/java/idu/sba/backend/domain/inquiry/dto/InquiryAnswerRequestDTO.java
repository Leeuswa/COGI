package idu.sba.backend.domain.inquiry.dto;

import jakarta.validation.constraints.NotBlank;

public record InquiryAnswerRequestDTO(
        @NotBlank String answer
) {}
