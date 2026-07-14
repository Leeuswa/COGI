package idu.sba.backend.domain.context.dto;

import lombok.Getter;

@Getter
public class MethodSummaryDTO {

    private final String signature;
    private final String javadoc; //없으면 null
    private final int startLine;
    private final int endLine;

    private MethodSummaryDTO(String signature, String javadoc, int startLine, int endLine){
        this.signature = signature;
        this.javadoc = javadoc;
        this.startLine = startLine;
        this.endLine = endLine;
    }

    public static MethodSummaryDTO of(String signature, String javadoc, int startLine, int endLine){
        return new MethodSummaryDTO(signature, javadoc, startLine, endLine);
    }

}
