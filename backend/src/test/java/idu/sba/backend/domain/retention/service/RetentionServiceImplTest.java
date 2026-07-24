package idu.sba.backend.domain.retention.service;

import idu.sba.backend.domain.retention.dto.RetentionStatusResponseDTO;
import idu.sba.backend.domain.retention.entity.UserStreak;
import idu.sba.backend.domain.retention.repository.UserStreakRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetentionServiceImplTest {

    @Mock private UserStreakRepository userStreakRepository;

    @InjectMocks
    private RetentionServiceImpl service;

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

    private UserStreak streak(int current, LocalDate lastDate) {
        UserStreak s = UserStreak.create(USER_ID);
        setField(s, "currentStreak", current);
        setField(s, "lastCompletedDate", lastDate);
        return s;
    }

    // ---------- 엔티티 갱신 로직 ----------

    @Test
    void 첫_제출이면_streak가_1이_된다() {
        UserStreak s = UserStreak.create(USER_ID);
        s.recordSubmission(LocalDate.of(2026, 7, 22));
        assertThat(s.getCurrentStreak()).isEqualTo(1);
    }

    @Test
    void 어제에_이어_오늘_제출하면_1_증가() {
        UserStreak s = streak(3, LocalDate.of(2026, 7, 21));
        s.recordSubmission(LocalDate.of(2026, 7, 22));
        assertThat(s.getCurrentStreak()).isEqualTo(4);
    }

    @Test
    void 오늘_이미_제출했으면_그대로() {
        UserStreak s = streak(4, LocalDate.of(2026, 7, 22));
        s.recordSubmission(LocalDate.of(2026, 7, 22));
        assertThat(s.getCurrentStreak()).isEqualTo(4);
    }

    @Test
    void 하루_이상_걸러_제출하면_1로_초기화() {
        UserStreak s = streak(10, LocalDate.of(2026, 7, 19)); // 3일 전
        s.recordSubmission(LocalDate.of(2026, 7, 22));
        assertThat(s.getCurrentStreak()).isEqualTo(1);
    }

    @Test
    void 어제까지_이어졌으면_오늘_조회_시_유효() {
        UserStreak s = streak(5, LocalDate.of(2026, 7, 21));
        assertThat(s.effectiveStreak(LocalDate.of(2026, 7, 22))).isEqualTo(5); // 오늘 낼 수 있으니 살아있음
    }

    @Test
    void 이틀_이상_끊겼으면_조회_시_0() {
        UserStreak s = streak(5, LocalDate.of(2026, 7, 19));
        assertThat(s.effectiveStreak(LocalDate.of(2026, 7, 22))).isZero();
    }

    // ---------- 서비스 ----------

    @Test
    void 제출_이력이_없으면_streak_0() {
        when(userStreakRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        RetentionStatusResponseDTO result = service.getRetentionStatus(USER_ID);

        assertThat(result.getStreak()).isZero();
        assertThat(result.isSubmittedToday()).isFalse();
    }

    @Test
    void 신규_사용자_제출이면_새_streak를_저장한다() {
        when(userStreakRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(userStreakRepository.save(any(UserStreak.class))).thenAnswer(inv -> inv.getArgument(0));

        service.recordSubmission(USER_ID);
        // 예외 없이 저장 경로를 타면 성공 (streak 로직 자체는 엔티티 테스트에서 검증)
    }
}
