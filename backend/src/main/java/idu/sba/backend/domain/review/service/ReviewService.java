package idu.sba.backend.domain.review.service;

import idu.sba.backend.domain.review.dto.ReviewPasteRequestDTO;
import idu.sba.backend.domain.review.dto.ReviewResultResponseDTO;
import idu.sba.backend.domain.review.dto.ReviewUploadRequestDTO;

public interface ReviewService {

    // API-040: 로그인 사용자 코드 붙여넣기 리뷰
    ReviewResultResponseDTO createFromPaste(Long userId, ReviewPasteRequestDTO request);

    // API-041: 로그인 사용자 파일 업로드 리뷰
    ReviewResultResponseDTO createFromUpload(Long userId, ReviewUploadRequestDTO request);

}
