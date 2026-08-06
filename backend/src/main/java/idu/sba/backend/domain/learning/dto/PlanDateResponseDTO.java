package idu.sba.backend.domain.learning.dto;

import java.time.LocalDate;

/**
 * 등록된 학습 계획의 한 단계 = 달력 한 칸.
 *
 * 날짜는 저장하지 않고 카드의 planStartedAt + 단계의 dayOffset으로 그때그때 계산한다.
 * 대시보드 달력 표시와 오늘 알림 문구가 같은 값을 쓴다.
 */
public record PlanDateResponseDTO(
        LocalDate date,
        Long cardId,
        String category,   // 카드 주제 — 알림 제목에 쓴다
        int stepNo,        // 1부터. 알림 링크(?step=)와 화면 강조에 쓴다
        String title,
        String focus,
        Integer minutes
) {}
