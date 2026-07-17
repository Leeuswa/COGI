package idu.sba.backend.domain.guest.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

// Redis에 저장하는 게스트 리뷰 원본. 회원가입 claim 시 이 데이터로 reviews 행을 복원한다.
// 프론트 응답(GuestReviewResponse)과 달리 원본 code/language/model 까지 보관한다.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GuestReviewRecord {
    private String reviewId;
    private String summary;
    private List<ReviewComment> comments;
    private String code;
    private String language;
    private String modelName;
}
