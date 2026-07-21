package idu.sba.backend.domain.guest.dto;


import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter //값을 꺼내기 위하여 Getter만 필요
@NoArgsConstructor
public class GuestReviewRequest {
    private String code; //리뷰받을 코드
    private String level; //설명 수준 BEGINNER/INTERMEDIATE/ADVANCED (없거나 이상하면 BEGINNER)
}
