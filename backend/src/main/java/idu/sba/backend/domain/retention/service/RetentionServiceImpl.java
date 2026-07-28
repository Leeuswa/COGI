package idu.sba.backend.domain.retention.service;

import idu.sba.backend.domain.retention.dto.PetStateResponseDTO;
import idu.sba.backend.domain.retention.dto.PetStateUpdateRequestDTO;
import idu.sba.backend.domain.retention.dto.RetentionStatusResponseDTO;
import idu.sba.backend.domain.retention.entity.PetState;
import idu.sba.backend.domain.retention.entity.UserStreak;
import idu.sba.backend.domain.retention.repository.PetStateRepository;
import idu.sba.backend.domain.retention.repository.UserStreakRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class RetentionServiceImpl implements RetentionService {

    private final UserStreakRepository userStreakRepository;
    private final PetStateRepository petStateRepository;

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

    @Override
    @Transactional
    public PetStateResponseDTO getPetState(Long userId) {
        // 첫 조회면 기본 스탯으로 만들어 준다 (회원가입 때 미리 만들지 않는 이유 — 안 쓰는 계정까지 행을 남길 필요가 없다)
        return PetStateResponseDTO.of(requirePet(userId));
    }

    @Override
    @Transactional
    public PetStateResponseDTO updatePetState(Long userId, PetStateUpdateRequestDTO request) {
        PetState pet = requirePet(userId);
        pet.update(request.getCoins(), request.getFullness(), request.getHappiness(),
                request.getCleanliness(), request.getXp(), request.getLastCheckIn()); // 더티체킹으로 반영
        return PetStateResponseDTO.of(pet);
    }

    // 내 코기 가져오기 — 없으면 만들어서 돌려준다
    private PetState requirePet(Long userId) {
        return petStateRepository.findByUserId(userId)
                .orElseGet(() -> petStateRepository.save(PetState.create(userId)));
    }
}
