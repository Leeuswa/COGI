package idu.sba.backend.global.exception;

import idu.sba.backend.global.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

    @RestControllerAdvice  // 모든 @RestController 에서 터진 예외를 여기서 가로챈다 (전역 그물)
    public class GlobalExceptionHandler {

        //의도적으로 던진 BusinessException 처리
        @ExceptionHandler(BusinessException.class)
        public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e){
            ErrorCode code = e.getErrorCode();
            return ResponseEntity
                    .status(code.getStatus())
                    .body(ApiResponse.fail(code.getMessage()));
        }

        //@Vaild 형식 검증 실패 처리
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e){

            String message = e.getBindingResult().getFieldErrors().stream()
                    .findFirst()
                    .map(fieldError -> fieldError.getDefaultMessage())
                    .orElse(ErrorCode.INVALID_INPUT.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail(message));
        }

        //업로드 파일(API-041)이 multipart 용량 제한을 넘은 경우 — 기본 에러 페이지 대신 우리 응답 형식으로 통일
        @ExceptionHandler(MaxUploadSizeExceededException.class)
        public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException e){
            return ResponseEntity
                    .status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(ApiResponse.fail("업로드 파일 용량이 너무 큽니다."));
        }
    }
