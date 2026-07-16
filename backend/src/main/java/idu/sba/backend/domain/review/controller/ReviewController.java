package idu.sba.backend.domain.review.controller;

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

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

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

    private String readFileContent(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

}
