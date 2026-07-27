package idu.sba.backend.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record FaqRequestDTO(
        @NotBlank
        String question,
        @NotBlank
        String answer,
        String category,
        boolean visible
) {}
