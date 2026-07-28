package idu.sba.backend.domain.retention.dto;

import idu.sba.backend.domain.retention.entity.PetState;
import lombok.Getter;

import java.time.LocalDate;

// 코기 상태 응답 — 프론트 GameContext가 읽는 필드명에 맞춤
@Getter
public class PetStateResponseDTO {

    private final int coins;
    private final int fullness;
    private final int happiness;
    private final int cleanliness;
    private final int xp;
    private final LocalDate lastCheckIn; // 아직 안 받았으면 null

    private PetStateResponseDTO(PetState pet) {
        this.coins = pet.getCoins();
        this.fullness = pet.getFullness();
        this.happiness = pet.getHappiness();
        this.cleanliness = pet.getCleanliness();
        this.xp = pet.getXp();
        this.lastCheckIn = pet.getLastCheckIn();
    }

    public static PetStateResponseDTO of(PetState pet) {
        return new PetStateResponseDTO(pet);
    }
}
