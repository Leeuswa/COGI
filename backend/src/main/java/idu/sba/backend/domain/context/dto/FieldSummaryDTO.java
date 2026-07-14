package idu.sba.backend.domain.context.dto;

import lombok.Getter;

@Getter
public class FieldSummaryDTO {

    private final String declaration;
    private final int lineNumber;

    private FieldSummaryDTO(String declaration, int lineNumber){
        this.declaration = declaration;
        this.lineNumber = lineNumber;
    }

    public static FieldSummaryDTO of(String declaration, int lineNumber){
        return new FieldSummaryDTO(declaration, lineNumber);
    }

}
