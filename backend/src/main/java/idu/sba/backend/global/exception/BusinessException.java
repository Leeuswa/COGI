package idu.sba.backend.global.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException{

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode){
        super(errorCode.getMessage()); // 부모(Exception)에 메시지 전달 → 로그에 메시지 찍히게
        this.errorCode =errorCode;
    }


}
