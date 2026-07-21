package idu.sba.backend.domain.review.service;

import idu.sba.backend.domain.pr.dto.PrReviewImportRequestDTO;
import idu.sba.backend.domain.review.dto.ReviewHistoryDetailResponseDTO;
import idu.sba.backend.domain.review.dto.ReviewHistoryItemResponseDTO;
import idu.sba.backend.domain.review.dto.ReviewPasteRequestDTO;
import idu.sba.backend.domain.review.dto.ReviewResultResponseDTO;
import idu.sba.backend.domain.review.dto.ReviewUploadRequestDTO;

import java.util.List;

public interface ReviewService {

    // API-040: 로그인 사용자 코드 붙여넣기 리뷰
    ReviewResultResponseDTO createFromPaste(Long userId, ReviewPasteRequestDTO request);

    // API-041: 로그인 사용자 파일 업로드 리뷰
    ReviewResultResponseDTO createFromUpload(Long userId, ReviewUploadRequestDTO request);

    // API-027: 내 요금제에서 선택 가능한 모델 목록
    List<String> getModelOptions(Long userId);

    // API-024: 웹훅으로 감지된 PR을 리뷰 — HTTP 호출자가 없어 응답 DTO 없음(결과는 API-025로 조회).
    // 실패해도 예외를 던지지 않고 PullRequest.status로 흡수한다(GithubWebhookServiceImpl 참고).
    void createFromPr(Long pullRequestId);

    // 리뷰 히스토리 [설계 추론]: 로그인 사용자가 만든 개별 리뷰(PASTE/UPLOAD/PR/GUEST) 목록
    List<ReviewHistoryItemResponseDTO> getHistory(Long userId);

    // 리뷰 히스토리 상세 [설계 추론]: 본인 리뷰가 아니면 REVIEW_ACCESS_DENIED
    ReviewHistoryDetailResponseDTO getHistoryDetail(Long userId, Long reviewId);

    // Studio "PR 가져오기"로 고른 파일을 리뷰 [설계 추론] — 웹훅 경로와 달리 호출자 본인 크레딧/동기 응답.
    // target_type=PR로 저장돼 API-025(GET /api/prs/{prId}/review)에서도 조회 가능해진다.
    ReviewResultResponseDTO createFromPrImport(Long callerId, Long repoId, Integer prNumber, PrReviewImportRequestDTO request);

}
