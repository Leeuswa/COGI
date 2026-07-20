package idu.sba.backend.domain.pr.service;

import idu.sba.backend.domain.pr.dto.PrFileResponseDTO;
import idu.sba.backend.domain.pr.dto.PrReviewResponseDTO;
import idu.sba.backend.domain.pr.dto.PrSummaryResponseDTO;

import java.util.List;

public interface PrService {

    // API-025: PR 리뷰 결과(이슈 목록) 조회 — 호출자가 그 레포의 팀원이어야 함
    PrReviewResponseDTO getPrReview(Long currentUserId, Long prId);

    // Studio "PR 가져오기" 피커 1단계 — 레포의 열린 PR 목록(GitHub 실시간 조회, 우리 PullRequest 테이블과 무관)
    List<PrSummaryResponseDTO> listOpenPrs(Long currentUserId, Long repoId);

    // Studio "PR 가져오기" 피커 2단계 — 특정 PR의 변경 파일 목록(코드는 diff 근사치, PrFileResponseDTO 참고)
    List<PrFileResponseDTO> listPrFiles(Long currentUserId, Long repoId, int prNumber);

}
