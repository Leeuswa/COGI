package idu.sba.backend.domain.learning.dto;

import lombok.Getter;

// 완료 체크 토글 요청 (API-044-2) — 프론트가 { completed }를 보낸다
@Getter
public class LearningCardCompleteRequestDTO {
    private boolean completed;
}
