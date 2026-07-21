package idu.sba.backend.domain.growth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GrowthTrendResponseDTO {

    private final String label; // 예시: "6/15"
    private final long issueCount;
    private final long resolved;

}
