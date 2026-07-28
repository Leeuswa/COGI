package idu.sba.backend.domain.retention.controller;

import idu.sba.backend.domain.retention.dto.PetStateResponseDTO;
import idu.sba.backend.domain.retention.dto.PetStateUpdateRequestDTO;
import idu.sba.backend.domain.retention.dto.RetentionStatusResponseDTO;
import idu.sba.backend.domain.retention.service.RetentionService;
import idu.sba.backend.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class RetentionController {

    private final RetentionService retentionService;

    // API-051: 현재 streak·오늘 제출 여부 조회
    @GetMapping("/retention-status")
    public ApiResponse<RetentionStatusResponseDTO> getRetentionStatus(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(retentionService.getRetentionStatus(userId));
    }

    // 다마고치 상태 조회 — 계정별로 따로 자란다. 처음이면 기본 스탯으로 만들어 준다
    @GetMapping("/pet")
    public ApiResponse<PetStateResponseDTO> getPetState(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(retentionService.getPetState(userId));
    }

    // 다마고치 상태 저장 — 먹이·산책·청소·코인 변화가 있을 때 프론트가 통째로 보낸다
    @PutMapping("/pet")
    public ApiResponse<PetStateResponseDTO> updatePetState(@AuthenticationPrincipal Long userId,
                                                           @RequestBody PetStateUpdateRequestDTO request) {
        return ApiResponse.ok(retentionService.updatePetState(userId, request));
    }
}
