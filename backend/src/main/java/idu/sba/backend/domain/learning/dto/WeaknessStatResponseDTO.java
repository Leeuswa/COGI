package idu.sba.backend.domain.learning.dto;

import idu.sba.backend.domain.learning.entity.WeaknessStat;
import lombok.Getter;

import java.time.format.DateTimeFormatter;
import java.util.Objects;

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

    // 저장하지 않고 그때그때 집계한 값을 그대로 내려준다 (LearningServiceImpl.getWeaknessStats 주석 참고).
    // id는 DB 시퀀스가 아니라 (카테고리, 언어)에서 뽑는다 — 화면이 목록 key로만 쓰고,
    // 이 id로 조회하는 API는 없다. 같은 약점이면 새로고침해도 같은 값이 나온다
    public static WeaknessStatResponseDTO of(WeaknessStat stat) {
        String updatedAt = stat.getUpdatedAt() != null
                ? stat.getUpdatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE)
                : null;
        return new WeaknessStatResponseDTO(rowId(stat.getCategory(), stat.getLanguage()),
                stat.getCategory(), stat.getLanguage(), stat.getOccurrenceCount(), updatedAt, null);
    }

    private static Long rowId(String category, String language) {
        return (long) Objects.hash(category, language) & 0xffffffffL; // 음수는 화면에서 어색하니 양수로
    }
}
