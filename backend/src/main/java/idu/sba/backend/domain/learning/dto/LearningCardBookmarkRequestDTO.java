package idu.sba.backend.domain.learning.dto;

import lombok.Getter;

// 북마크 토글 요청 (API-044-1) — 프론트가 { bookmarked }를 보낸다
@Getter
public class LearningCardBookmarkRequestDTO {
    private boolean bookmarked;
}
