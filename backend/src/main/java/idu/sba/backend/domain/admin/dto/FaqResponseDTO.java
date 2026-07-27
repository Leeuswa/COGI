package idu.sba.backend.domain.admin.dto;

import idu.sba.backend.domain.admin.entity.Faq;

public record FaqResponseDTO(
        Long id, String question, String answer, String category, boolean visible
) {
    public static FaqResponseDTO of(Faq f) {
        return new FaqResponseDTO(f.getId(), f.getQuestion(), f.getAnswer(),
                f.getCategory(), f.isVisible());
    }
}
