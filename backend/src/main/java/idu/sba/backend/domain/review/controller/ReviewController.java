package idu.sba.backend.domain.review.controller;

import idu.sba.backend.domain.review.dto.ReviewHistoryDetailResponseDTO;
import idu.sba.backend.domain.review.dto.ReviewHistoryItemResponseDTO;
import idu.sba.backend.domain.review.dto.ReviewPasteRequestDTO;
import idu.sba.backend.domain.review.dto.ReviewResultResponseDTO;
import idu.sba.backend.domain.review.dto.ReviewUploadRequestDTO;
import idu.sba.backend.domain.review.service.ReviewService;
import idu.sba.backend.global.common.ApiResponse;
import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    //API-027: 내 요금제에서 선택 가능한 모델 목록
    @GetMapping("/model-options")
    public ApiResponse<List<String>> getModelOptions(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(reviewService.getModelOptions(userId));
    }

    //API-040: 로그인 사용자 코드 붙여넣기 리뷰
    @PostMapping("/paste")
    public ApiResponse<ReviewResultResponseDTO> createFromPaste(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ReviewPasteRequestDTO request) {
        return ApiResponse.ok(reviewService.createFromPaste(userId, request));
    }

    //API-041: 로그인 사용자 파일 업로드 리뷰
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ApiResponse<ReviewResultResponseDTO> createFromUpload(
            @AuthenticationPrincipal Long userId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String modelName) {

        String code = readFileContent(file);
        ReviewUploadRequestDTO request = new ReviewUploadRequestDTO(code, language, file.getOriginalFilename(), modelName);
        return ApiResponse.ok(reviewService.createFromUpload(userId, request));
    }

    // [설계 추론] 리뷰 히스토리 — 붙여넣기/업로드/PR가져오기로 만든 개별 리뷰 목록(대시보드 집계와 별개)
    @GetMapping("/history")
    public ApiResponse<List<ReviewHistoryItemResponseDTO>> getHistory(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(reviewService.getHistory(userId));
    }

    // [설계 추론] 리뷰 히스토리 상세 — 개별 리뷰의 이슈 목록
    @GetMapping("/history/{reviewId}")
    public ApiResponse<ReviewHistoryDetailResponseDTO> getHistoryDetail(
            @AuthenticationPrincipal Long userId, @PathVariable Long reviewId) {
        return ApiResponse.ok(reviewService.getHistoryDetail(userId, reviewId));
    }

    private String readFileContent(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

}
