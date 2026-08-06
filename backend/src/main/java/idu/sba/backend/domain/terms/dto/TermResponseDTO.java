package idu.sba.backend.domain.terms.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import idu.sba.backend.domain.terms.entity.Term;
import idu.sba.backend.domain.terms.entity.TermType;
import lombok.Getter;

@Getter
public class TermResponseDTO {
    private final Long id;
    private final TermType type;
    private final String version;
    private final String title;

    @JsonProperty("isRequired")     // JSON 키는 isRequired 로 나감 (프론트가 읽는 이름)
    private final boolean mandatory;
    private final String content;

    private TermResponseDTO(Term t) {
        this.id = t.getId();
        this.type = t.getType();
        this.version = t.getVersion();
        this.title = t.getTitle();
        this.mandatory = Boolean.TRUE.equals(t.getIsRequired());
        this.content = t.getContent();
    }

    public static TermResponseDTO from(Term t) {
        return new TermResponseDTO(t);
    }
}