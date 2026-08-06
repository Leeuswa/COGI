package idu.sba.backend.domain.guest.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter            //Redis JSON 역직렬화(claim)용
@NoArgsConstructor //Redis JSON 역직렬화(claim)용
@AllArgsConstructor
public class ReviewComment { //이슈 한건의 DTO
    private String category; //BUG / PERFORMANCE / CODE_SMELL / CONVENTION / SECURITY
    private String severity; //CRITICAL / MAJOR / MINOR
    private String filePath; //이슈 파일 경로
    private Integer lineNumber; //이슈 라인
    private String description; //이슈 설명
}
