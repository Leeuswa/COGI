package idu.sba.backend.global.exception;

import idu.sba.backend.global.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
    }
