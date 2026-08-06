package idu.sba.backend.domain.context.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CodeContextResponseDTO {

    private String filePath;
    private String packageName;     //없으면 null
    private List<String> imports;
    private String className;       //없으면 null
    private MethodSummaryDTO enclosingMethod; //startLine이 속한 메서드, 없으면 null
    private List<MethodSummaryDTO> siblingMethods; //같은 클래스의 다른 메서드들
    private List<FieldSummaryDTO> fields;
    private List<String> referencedLocalTypes; //이름 매칭 기반 best-effort
    private String parseWarning; //파싱이 완전히 실패했을 때만 설정, 성공이면 null

}
