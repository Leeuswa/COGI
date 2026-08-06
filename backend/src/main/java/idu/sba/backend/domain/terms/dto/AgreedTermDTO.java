package idu.sba.backend.domain.terms.dto;

import idu.sba.backend.domain.terms.entity.UserAgreement;
import lombok.Getter;

// 내 약관 동의 상세 — 마이페이지에서 "동의 당시 버전 != 현재 버전"(개정됨) 판정에 사용.
@Getter
public class AgreedTermDTO {
    private final Long termId;
    private final String agreedVersion; // 동의 당시 버전 (레거시 동의는 null 가능)

    private AgreedTermDTO(Long termId, String agreedVersion) {
        this.termId = termId;
        this.agreedVersion = agreedVersion;
    }

    public static AgreedTermDTO from(UserAgreement a) {
        return new AgreedTermDTO(a.getTermId(), a.getAgreedVersion());
    }
}
