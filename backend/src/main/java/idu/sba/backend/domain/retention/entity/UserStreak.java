package idu.sba.backend.domain.retention.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 사용자별 연속 학습일(streak) — 퀴즈 제출 시점 기준으로 갱신된다(정답 여부 무관, RET-001)
@Entity
@Table(name = "user_streaks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserStreak {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private Long userId; // 사용자당 1행

    private int currentStreak;

    private LocalDate lastCompletedDate; // 마지막 제출일, nullable

    private LocalDateTime updatedAt;

    private UserStreak(Long userId) {
        this.userId = userId;
        this.currentStreak = 0;
    }

    public static UserStreak create(Long userId) {
        return new UserStreak(userId);
    }

    // 제출 반영 (FR-73/74) — 오늘 첫 제출이면 연속일 갱신, 이미 오늘 냈으면 그대로
    public void recordSubmission(LocalDate today) {
        if (today.equals(lastCompletedDate)) {
            // 오늘 이미 제출 — 변화 없음
        } else if (today.minusDays(1).equals(lastCompletedDate)) {
            this.currentStreak++; // 어제에 이어 오늘 → 연속
        } else {
            this.currentStreak = 1; // 첫 제출이거나 하루 이상 걸러서 끊김
        }
        this.lastCompletedDate = today;
        this.updatedAt = LocalDateTime.now();
    }

    // 표시용 streak — 자정이 지나도록(어제까지도) 제출이 없으면 끊긴 것으로 보고 0
    public int effectiveStreak(LocalDate today) {
        if (lastCompletedDate == null) {
            return 0;
        }
        boolean stillAlive = lastCompletedDate.equals(today) || lastCompletedDate.equals(today.minusDays(1));
        return stillAlive ? currentStreak : 0;
    }

    public boolean submittedToday(LocalDate today) {
        return today.equals(lastCompletedDate);
    }
}
