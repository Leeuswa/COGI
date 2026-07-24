package idu.sba.backend.domain.retention.service;

import idu.sba.backend.domain.retention.dto.RetentionStatusResponseDTO;
import idu.sba.backend.domain.retention.entity.UserStreak;
import idu.sba.backend.domain.retention.repository.UserStreakRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class RetentionServiceImpl implements RetentionService {

    private final UserStreakRepository userStreakRepository;

    @Override
    @Transactional
    public void recordSubmission(Long userId) {
        LocalDate today = LocalDate.now();
        UserStreak streak = userStreakRepository.findByUserId(userId)
                .orElseGet(() -> UserStreak.create(userId));
        streak.recordSubmission(today);
        userStreakRepository.save(streak); // 신규면 insert, 기존이면 갱신
    }

    @Override
    @Transactional(readOnly = true)
    public RetentionStatusResponseDTO getRetentionStatus(Long userId) {
        LocalDate today = LocalDate.now();
        // 없으면 아직 제출 이력 없는 사용자 → streak 0
        return userStreakRepository.findByUserId(userId)
                .map(streak -> RetentionStatusResponseDTO.of(streak.effectiveStreak(today), streak.submittedToday(today)))
                .orElseGet(() -> RetentionStatusResponseDTO.of(0, false));
    }
}
