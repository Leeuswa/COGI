package idu.sba.backend.domain.growth.service;


import idu.sba.backend.domain.growth.dto.GrowthCompareResponseDTO;
import idu.sba.backend.domain.growth.dto.GrowthTrendResponseDTO;

import java.util.List;

public interface GrowthService {

    // 개인 또는 팀 총합의 주간 발생/해결 추이 (권한·집계·빈 주 채움은 구현체 담당)
    List<GrowthTrendResponseDTO> getGrowthTrend(Long currentUserId, Long teamId, String period,
                                                Long memberId);

    // 팀장이 보는 팀원별 발생 수 겹쳐보기
    GrowthCompareResponseDTO getGrowthCompare(Long currentUserId, Long teamId, String period);

}
