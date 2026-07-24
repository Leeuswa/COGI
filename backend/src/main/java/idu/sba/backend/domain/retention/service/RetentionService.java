package idu.sba.backend.domain.retention.service;

import idu.sba.backend.domain.retention.dto.RetentionStatusResponseDTO;

public interface RetentionService {

    // 퀴즈 제출 시 streak 갱신 (RET-001) — 정답 여부와 무관, 학습카드 제출 경로에서 호출
    void recordSubmission(Long userId);

    // 현재 streak·오늘 제출 여부 조회 (API-051)
    RetentionStatusResponseDTO getRetentionStatus(Long userId);
}
