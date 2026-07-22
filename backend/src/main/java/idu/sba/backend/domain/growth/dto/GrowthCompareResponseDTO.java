package idu.sba.backend.domain.growth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class GrowthCompareResponseDTO {

    private final List<String> labels; //x축 주 라벨: ["6/15","6/22","6/29","7/6"]
    private final List<Series> series; //사람별 선

    @Getter
    @AllArgsConstructor
    public static class Series{
        private final String member; //깃허브이름
        private final List<Long> issues; //주별 발생 수

    }

}
