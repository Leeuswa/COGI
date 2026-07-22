package idu.sba.backend.domain.learning.service;

import idu.sba.backend.domain.learning.dto.WeaknessStatResponseDTO;
import idu.sba.backend.domain.learning.entity.WeaknessStat;
import idu.sba.backend.domain.learning.repository.WeaknessStatRepository;
import idu.sba.backend.domain.review.entity.IssueCategory;
import idu.sba.backend.domain.review.entity.IssueSeverity;
import idu.sba.backend.domain.review.entity.IssueStatus;
import idu.sba.backend.domain.review.entity.Review;
import idu.sba.backend.domain.review.entity.ReviewIssue;
import idu.sba.backend.domain.review.repository.ReviewIssueRepository;
import idu.sba.backend.domain.review.repository.ReviewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningServiceImplTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private ReviewIssueRepository reviewIssueRepository;
    @Mock private WeaknessStatRepository weaknessStatRepository;

    @InjectMocks
    private LearningServiceImpl service;

    private static final Long USER_ID = 1L;

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private Review review(Long id, String language) {
        Review review = Review.createPaste(USER_ID, "code", language, "claude-haiku-4-5");
        setField(review, "id", id);
        return review;
    }

    private ReviewIssue issue(Long reviewId, IssueCategory category) {
        ReviewIssue issue = ReviewIssue.of(reviewId, category, IssueSeverity.MAJOR, "Foo.java", 10, "설명");
        setField(issue, "createdAt", LocalDateTime.now());
        return issue;
    }

    // 저장 결과를 그대로 돌려주도록(saveAll이 인자 리스트를 반환) 스텁
    private void stubSaveEcho() {
        when(weaknessStatRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void 같은_카테고리가_3회_이상이면_통계에_잡힌다() {
        stubSaveEcho();
        Review review = review(10L, "Java");
        when(reviewRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(review));
        when(reviewIssueRepository.findByReviewIdIn(any())).thenReturn(List.of(
                issue(10L, IssueCategory.BUG),
                issue(10L, IssueCategory.BUG),
                issue(10L, IssueCategory.BUG)));

        List<WeaknessStatResponseDTO> result = service.getWeaknessStats(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCategory()).isEqualTo("BUG");
        assertThat(result.get(0).getLanguage()).isEqualTo("Java");
        assertThat(result.get(0).getOccurrenceCount()).isEqualTo(3);
        assertThat(result.get(0).getCardId()).isNull();
    }

    @Test
    void 같은_카테고리라도_3회_미만이면_제외된다() {
        stubSaveEcho();
        Review review = review(10L, "Java");
        when(reviewRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(review));
        when(reviewIssueRepository.findByReviewIdIn(any())).thenReturn(List.of(
                issue(10L, IssueCategory.BUG),
                issue(10L, IssueCategory.BUG)));

        List<WeaknessStatResponseDTO> result = service.getWeaknessStats(USER_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void acknowledged_이슈는_카운트에서_빠진다() {
        stubSaveEcho();
        Review review = review(10L, "Java");
        when(reviewRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(review));

        ReviewIssue ignored = issue(10L, IssueCategory.BUG);
        ignored.finalizeAs(IssueStatus.IGNORED); // 의도한 코드 → acknowledged=true → 제외
        when(reviewIssueRepository.findByReviewIdIn(any())).thenReturn(List.of(
                issue(10L, IssueCategory.BUG),
                issue(10L, IssueCategory.BUG),
                ignored));

        List<WeaknessStatResponseDTO> result = service.getWeaknessStats(USER_ID);

        // 유효 이슈는 2건뿐이라 3회 미만 → 통계 없음
        assertThat(result).isEmpty();
    }

    @Test
    void 언어가_다르면_따로_집계된다() {
        stubSaveEcho();
        Review java = review(10L, "Java");
        Review python = review(20L, "Python");
        when(reviewRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(java, python));
        when(reviewIssueRepository.findByReviewIdIn(any())).thenReturn(List.of(
                issue(10L, IssueCategory.BUG),
                issue(10L, IssueCategory.BUG),
                issue(10L, IssueCategory.BUG),
                issue(20L, IssueCategory.BUG),
                issue(20L, IssueCategory.BUG),
                issue(20L, IssueCategory.BUG)));

        List<WeaknessStatResponseDTO> result = service.getWeaknessStats(USER_ID);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(WeaknessStatResponseDTO::getLanguage)
                .containsExactlyInAnyOrder("Java", "Python");
    }

    @Test
    void 리뷰가_없으면_빈_목록이다() {
        stubSaveEcho();
        when(reviewRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of());
        when(reviewIssueRepository.findByReviewIdIn(any())).thenReturn(List.of());

        List<WeaknessStatResponseDTO> result = service.getWeaknessStats(USER_ID);

        assertThat(result).isEmpty();
    }
}
