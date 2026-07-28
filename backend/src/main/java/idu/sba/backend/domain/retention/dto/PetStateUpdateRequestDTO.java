package idu.sba.backend.domain.retention.dto;

import lombok.Getter;

import java.time.LocalDate;

// 코기 상태 저장 요청 — 스탯을 통째로 보낸다
@Getter
public class PetStateUpdateRequestDTO {
    private int coins;
    private int fullness;
    private int happiness;
    private int cleanliness;
    private int xp;
    private LocalDate lastCheckIn; // 오늘 출석을 안 받았으면 null
}
