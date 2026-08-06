package idu.sba.backend.domain.learning.dto;

import lombok.Getter;

// 카드 승급 히스토리 한 줄 (API-047) — 프론트가 { date, grade, note }를 읽는다
@Getter
public class CardHistoryResponseDTO {

    private final String date;
    private final String grade;
    private final String note;

    public CardHistoryResponseDTO(String date, String grade, String note) {
        this.date = date;
        this.grade = grade;
        this.note = note;
    }
}
