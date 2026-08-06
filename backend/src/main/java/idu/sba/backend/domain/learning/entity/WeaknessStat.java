package idu.sba.backend.domain.learning.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 사용자별 약점 통계 한 줄 = (카테고리, 언어) 조합의 누적 집계 (LRN-001)
// 원천은 reviews + review_issues이고, 이 테이블은 조회 시점에 다시 집계해 갈아끼운다
@Entity
@Table(name = "weakness_stats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeaknessStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private String category; // 이슈 카테고리 코드 (BUG/PERFORMANCE/CODE_SMELL/CONVENTION/SECURITY)

    private String language; // 프로그래밍 언어, 판별 안 됐으면 null (FR-65)

    private int occurrenceCount; // 누적 발생 횟수 (3회 이상만 저장, FR-56)

    private LocalDateTime updatedAt; // 이 그룹에서 마지막으로 이슈가 발생한 시각

    private WeaknessStat(Long userId, String category, String language,
                         int occurrenceCount, LocalDateTime updatedAt) {
        this.userId = userId;
        this.category = category;
        this.language = language;
        this.occurrenceCount = occurrenceCount;
        this.updatedAt = updatedAt;
    }

    public static WeaknessStat of(Long userId, String category, String language,
                                  int occurrenceCount, LocalDateTime updatedAt) {
        return new WeaknessStat(userId, category, language, occurrenceCount, updatedAt);
    }
}
