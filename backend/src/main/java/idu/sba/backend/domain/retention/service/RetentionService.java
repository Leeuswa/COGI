package idu.sba.backend.domain.retention.service;

import idu.sba.backend.domain.retention.dto.PetStateResponseDTO;
import idu.sba.backend.domain.retention.dto.PetStateUpdateRequestDTO;
import idu.sba.backend.domain.retention.dto.RetentionStatusResponseDTO;

public interface RetentionService {

    // 퀴즈 제출 시 streak 갱신 (RET-001) — 정답 여부와 무관, 학습카드 제출 경로에서 호출
    void recordSubmission(Long userId);

    // 현재 streak·오늘 제출 여부 조회 (API-051)
    RetentionStatusResponseDTO getRetentionStatus(Long userId);

    // 다마고치 상태 조회 — 처음이면 기본값으로 만들어 준다
    PetStateResponseDTO getPetState(Long userId);

    // 다마고치 상태 저장 — 스탯을 통째로 덮어쓴다
    PetStateResponseDTO updatePetState(Long userId, PetStateUpdateRequestDTO request);
}
