package idu.sba.backend.domain.learning.dto;

import idu.sba.backend.domain.learning.entity.WeaknessStat;
import lombok.Getter;

import java.time.format.DateTimeFormatter;

// 약점 통계 응답 한 줄 (프론트 Weakness.tsx가 읽는 필드명에 그대로 맞춤)
@Getter
public class WeaknessStatResponseDTO {

    private final Long id;
    private final String category; // enum 코드 그대로 (BUG 등)
    private final String language;
    private final int occurrenceCount;
    private final String updatedAt; // "마지막 발생" 표시용 날짜
    private final Long cardId; // 학습카드(LRN-002) 미구현이라 항상 null → 프론트가 '만들기' 버튼 노출

    private WeaknessStatResponseDTO(Long id, String category, String language,
                                   int occurrenceCount, String updatedAt, Long cardId) {
        this.id = id;
        this.category = category;
        this.language = language;
        this.occurrenceCount = occurrenceCount;
        this.updatedAt = updatedAt;
        this.cardId = cardId;
    }

    public static WeaknessStatResponseDTO of(WeaknessStat stat) {
        String updatedAt = stat.getUpdatedAt() != null
                ? stat.getUpdatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE)
                : null;
        return new WeaknessStatResponseDTO(stat.getId(), stat.getCategory(), stat.getLanguage(),
                stat.getOccurrenceCount(), updatedAt, null);
    }
}
