package idu.sba.backend.domain.learning.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

// AI가 생성한 학습카드 한 장 (LRN-002). 약점 카테고리별 개념+예제로 만들어진다.
@Entity
@Table(name = "learning_cards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LearningCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private String category;

    private String level; // BEGINNER/INTERMEDIATE/ADVANCED

    private String language; // nullable (FR-65)

    private String modelName; // 이 카드를 만든 AI 모델 — 이후 퀴즈 생성도 같은 모델로 한다

    @Lob
    private String conceptContent; // 개념 설명

    @Lob
    private String whyItMatters; // 방치하면 실무에서 뭐가 터지는지 (기존 카드는 null)

    @Lob
    private String exampleContent; // 대표 예제(엑셀 컬럼 유지) — 개선 예제와 같은 값을 둔다

    @Lob
    private String badExample; // 문제 있는 예제(프론트 렌더용)

    @Lob
    private String goodExample; // 개선된 예제

    @Lob
    private String diffExplain; // bad에서 good으로 뭘 왜 바꿨는지 (기존 카드는 null)

    @Lob
    private String keyPoints; // 핵심 포인트 — 줄바꿈으로 이어 저장, 응답에서 배열로 분리

    @Lob
    private String pitfalls; // 흔한 실수 — keyPoints와 같은 줄바꿈 저장 방식

    @Lob
    private String selfCheck; // 리뷰 전 셀프 체크 — 역시 줄바꿈 저장

    @Lob
    private String studyPlan; // AI가 짠 복습 계획 JSON 원문. 생성해야 채워지고 그 전엔 null

    // 계획을 캘린더에 "등록"한 날. 각 단계의 실제 날짜 = 이 날 + dayOffset 이라 날짜를 따로 저장하지 않는다.
    // null이면 계획만 짜 두고 아직 등록하지 않은 상태 — 달력에도 알림에도 안 뜬다
    private LocalDate planStartedAt;

    private String grade; // 신호등 등급 RED/YELLOW/GREEN/GREEN_PLUS, 생성 시 RED

    private int correctCount; // 누적 정답 수, 생성 시 0

    private boolean isBookmarked;

    private boolean isCompleted;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    private LearningCard(Long userId, String category, String level, String language, String modelName,
                         String conceptContent, String whyItMatters, String badExample, String goodExample,
                         String diffExplain, String keyPoints, String pitfalls, String selfCheck) {
        this.userId = userId;
        this.category = category;
        this.level = level;
        this.language = language;
        this.modelName = modelName;
        this.conceptContent = conceptContent;
        this.whyItMatters = whyItMatters;
        this.badExample = badExample;
        this.goodExample = goodExample;
        this.exampleContent = goodExample; // 엑셀 example_content는 개선 예제로 채운다
        this.diffExplain = diffExplain;
        this.keyPoints = keyPoints;
        this.pitfalls = pitfalls;
        this.selfCheck = selfCheck;
        this.grade = "RED"; // 새 카드는 학습 시작(빨강)
        this.correctCount = 0;
        this.isBookmarked = false;
        this.isCompleted = false;
    }

    public static LearningCard create(Long userId, String category, String level, String language, String modelName,
                                      String conceptContent, String whyItMatters, String badExample, String goodExample,
                                      String diffExplain, String keyPoints, String pitfalls, String selfCheck) {
        return new LearningCard(userId, category, level, language, modelName, conceptContent, whyItMatters,
                badExample, goodExample, diffExplain, keyPoints, pitfalls, selfCheck);
    }

    // 학습 계획은 나중에 따로 생성한다. 다시 만들면 이전 계획을 덮어쓴다.
    // 계획이 바뀌면 등록도 무효다 — 옛 등록일에 새 dayOffset을 더하면 엉뚱한 날짜가 나온다
    public void updateStudyPlan(String studyPlan) {
        this.studyPlan = studyPlan;
        this.planStartedAt = null;
    }

    // 계획을 달력에 등록 — 오늘을 기준일로 잡는다. 다시 누르면 오늘로 다시 시작한다
    public void registerStudyPlan(LocalDate startedAt) {
        this.planStartedAt = startedAt;
    }

    // 북마크 토글 (API-044-1)
    public void updateBookmark(boolean bookmarked) {
        this.isBookmarked = bookmarked;
    }

    // 완료 체크 토글 (API-044-2) — 등급과는 별개의 사용자 표시 상태
    public void updateCompleted(boolean completed) {
        this.isCompleted = completed;
    }

    // 정답 1회 반영 (API-046) — 정답만 카운트하고 신호등 등급을 다시 계산한다(오답은 호출 안 함, FR-61/62)
    public void recordCorrect() {
        this.correctCount++;
        this.grade = gradeFor(this.correctCount);
    }

    // 누적 정답 수 → 등급 (3=노랑, 7=초록, 8=해결+ / 프론트 규칙과 동일)
    private static String gradeFor(int correctCount) {
        if (correctCount >= 8) return "GREEN_PLUS";
        if (correctCount >= 7) return "GREEN";
        if (correctCount >= 3) return "YELLOW";
        return "RED";
    }
}
