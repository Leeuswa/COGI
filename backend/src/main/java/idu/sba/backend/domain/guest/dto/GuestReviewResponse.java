package idu.sba.backend.domain.guest.dto;

import idu.sba.backend.domain.guest.dto.ReviewComment;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class GuestReviewResponse {
    private String reviewId; //리뷰 식별자
    private String summary; // 리뷰 요약
    private List<ReviewComment> comments; //이슈 목록(리뷰 한번에 코멘트가 여러개나옴)
}
