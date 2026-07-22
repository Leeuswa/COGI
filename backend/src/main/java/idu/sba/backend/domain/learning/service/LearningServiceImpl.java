package idu.sba.backend.domain.learning.service;

import idu.sba.backend.domain.learning.dto.WeaknessStatResponseDTO;
import idu.sba.backend.domain.learning.entity.WeaknessStat;
import idu.sba.backend.domain.learning.repository.WeaknessStatRepository;
import idu.sba.backend.domain.review.entity.Review;
import idu.sba.backend.domain.review.entity.ReviewIssue;
import idu.sba.backend.domain.review.repository.ReviewIssueRepository;
import idu.sba.backend.domain.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LearningServiceImpl implements LearningService {

    private static final int MIN_OCCURRENCE = 3; // 3회 이상만 약점으로 집계 (FR-56)

    private final ReviewRepository reviewRepository;
    private final ReviewIssueRepository reviewIssueRepository;
    private final WeaknessStatRepository weaknessStatRepository;

    // (카테고리 코드, 언어)로 이슈를 묶는 집계 키 — 언어는 null 가능
    private record StatKey(String category, String language) {}

    @Override
    @Transactional
    public List<WeaknessStatResponseDTO> getWeaknessStats(Long userId) {
        // 내 리뷰 전부 조회 (PR/붙여넣기/업로드 포함 — 게스트는 userId가 없어 자동 제외, FR-55)
        List<Review> reviews = reviewRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<Long> reviewIds = reviews.stream().map(Review::getId).toList();

        // 리뷰별 언어 매핑 (language가 null 가능이라 toMap 대신 put으로)
        Map<Long, String> languageByReview = new HashMap<>();
        reviews.forEach(review -> languageByReview.put(review.getId(), review.getLanguage()));

        // acknowledged=false(진짜 약점)인 이슈만 (카테고리, 언어)로 묶기
        Map<StatKey, List<ReviewIssue>> grouped = reviewIssueRepository.findByReviewIdIn(reviewIds).stream()
                .filter(issue -> !issue.isAcknowledged())
                .collect(Collectors.groupingBy(issue ->
                        new StatKey(issue.getCategory().name(), languageByReview.get(issue.getReviewId()))));

        // 3회 이상만 통계 행으로 변환 (occurrenceCount=발생 수, updatedAt=마지막 발생 시각)
        List<WeaknessStat> stats = grouped.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= MIN_OCCURRENCE)
                .map(entry -> {
                    StatKey key = entry.getKey();
                    List<ReviewIssue> issues = entry.getValue();
                    LocalDateTime lastOccurred = issues.stream()
                            .map(ReviewIssue::getCreatedAt)
                            .max(Comparator.naturalOrder())
                            .orElse(null);
                    return WeaknessStat.of(userId, key.category(), key.language(), issues.size(), lastOccurred);
                })
                .toList();

        // 기존 통계를 비우고 새로 저장 (조회 시점 최신값으로 갈아끼우기)
        weaknessStatRepository.deleteByUserId(userId);
        List<WeaknessStat> saved = weaknessStatRepository.saveAll(stats);

        // 콜드스타트면 빈 목록 → 프론트가 안내문/버튼 처리
        return saved.stream().map(WeaknessStatResponseDTO::of).toList();
    }
}
