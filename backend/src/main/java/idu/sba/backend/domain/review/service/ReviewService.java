package idu.sba.backend.domain.review.service;

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

}
