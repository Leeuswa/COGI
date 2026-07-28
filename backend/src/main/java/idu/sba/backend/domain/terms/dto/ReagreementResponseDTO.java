package idu.sba.backend.domain.terms.dto;

import lombok.Getter;

import java.util.List;

// 약관 재동의 필요 여부 (FR-91). required=true 면 terms 에 담긴 개정 약관을 다시 동의받아야 한다.
@Getter
public class ReagreementResponseDTO {
    private final boolean required;
    private final List<TermResponseDTO> terms; // 재동의 대상(개정된 필수 약관, 본문 포함)

    public ReagreementResponseDTO(List<TermResponseDTO> terms) {
        this.required = !terms.isEmpty();
        this.terms = terms;
    }
}
