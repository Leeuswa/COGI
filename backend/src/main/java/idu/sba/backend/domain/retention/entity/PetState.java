package idu.sba.backend.domain.retention.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// 다마고치(포켓 코기) 상태. 사용자당 1행.
// 원래 localStorage에만 있어서 브라우저를 바꾸면 초기화됐다. 배포 후에도 이어지도록 DB로 옮겼다.
// streak·크레딧은 각각 user_streaks·credit_usage가 원천이라 여기 두지 않는다.
@Entity
@Table(name = "pet_states")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PetState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private Long userId; // 사용자당 1행

    private int coins; // 지갑

    private int fullness; // 배부름

    private int happiness; // 행복

    private int cleanliness; // 청결

    private int xp; // 경험치 — 레벨은 프론트가 이 값으로 계산한다

    private LocalDate lastCheckIn; // 출석 코인을 마지막으로 받은 날 (하루 1회 제한용)

    private PetState(Long userId) {
        this.userId = userId;
        this.coins = 30; // 프론트 DEFAULT와 같은 초기값
        this.fullness = 60;
        this.happiness = 60;
        this.cleanliness = 60;
        this.xp = 0;
    }

    // 새 사용자의 첫 코기
    public static PetState create(Long userId) {
        return new PetState(userId);
    }

    // 프론트가 스탯을 통째로 보내면 그대로 덮어쓴다. 부분 갱신 API를 따로 두지 않았다
    public void update(int coins, int fullness, int happiness, int cleanliness, int xp, LocalDate lastCheckIn) {
        this.coins = coins;
        this.fullness = fullness;
        this.happiness = happiness;
        this.cleanliness = cleanliness;
        this.xp = xp;
        this.lastCheckIn = lastCheckIn;
    }
}
