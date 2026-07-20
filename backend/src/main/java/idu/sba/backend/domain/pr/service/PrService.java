package idu.sba.backend.domain.pr.service;

import idu.sba.backend.domain.pr.dto.PrReviewResponseDTO;

public interface PrService {

    // API-025: PR 리뷰 결과(이슈 목록) 조회 — 호출자가 그 레포의 팀원이어야 함
    PrReviewResponseDTO getPrReview(Long currentUserId, Long prId);

}
