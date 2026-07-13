package idu.sba.backend.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

//    공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST,"입력값이 올바르지 않습니다."),

//    회원가입/인증
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT,"이미 가입된 이메일 입니다."), //409
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST,"비밀번호가 일치하지 않습니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST,"이메일 인증이 완료되지 않았습니다."),
    CODE_MISMATCH(HttpStatus.BAD_REQUEST,"인증 코드가 일치하지 않습니다."),
    CODE_EXPIRED(HttpStatus.GONE,"인증 코드가 만료되었습니다."),
    REQUIRED_TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST,"필수 약관에 모두 동의해야 합니다."),

    //로그인
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED,"이메일 또는 비밀번호가 일치하지 않습니다."), //401
    ACCOUNT_LOCKED(HttpStatus.LOCKED,"계정이 잠겼습니다. 비밀번호를 재설정해주세요"), //423
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN,"정지된 계정입니다."); //403

    private final HttpStatus status; // 이 에러가 나갈 때의 HTTP 상태코드
    private final String message; // 사용자에게 보여줄 메시지

}
