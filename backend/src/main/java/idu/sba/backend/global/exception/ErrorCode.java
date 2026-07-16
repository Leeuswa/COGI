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
    CODE_LOCKED(HttpStatus.LOCKED,"인증 시도가 일시 정지 되었습니다. 잠시 후 다시 시도해주세요."),// 423
    CODE_SEND_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS,"잠시 후 다시 시도해주세요.(60초)"), // 429
    REQUIRED_TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST,"필수 약관에 모두 동의해야 합니다."),

    //로그인
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED,"이메일 또는 비밀번호가 일치하지 않습니다."), //401
    ACCOUNT_LOCKED(HttpStatus.LOCKED,"계정이 잠겼습니다. 비밀번호를 재설정해주세요"), //423
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN,"정지된 계정입니다."), //403


    //비밀번호 재설정
    RESET_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "유효하지 않은 재설정 토큰입니다."),        // 400
    RESET_TOKEN_EXPIRED(HttpStatus.GONE, "재설정 토큰이 만료되었습니다."),                  // 410
    RESET_TOKEN_USED(HttpStatus.BAD_REQUEST, "이미 사용된 재설정 토큰입니다."),             // 400
    SAME_AS_OLD_PASSWORD(HttpStatus.BAD_REQUEST, "기존 비밀번호와 다른 비밀번호를 사용해주세요."), // 400
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."),               // 404

    //결제/구독
    SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 구독입니다."),              // 404
    PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 플랜입니다."),                     // 404
    PAYMENT_METHOD_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 결제수단입니다."),        // 404
    ALREADY_SUBSCRIBED(HttpStatus.CONFLICT, "이미 구독 중입니다."),                       // 409
    SUBSCRIPTION_FORBIDDEN(HttpStatus.FORBIDDEN, "본인의 구독만 접근할 수 있습니다."),      // 403
    PAYMENT_METHOD_FORBIDDEN(HttpStatus.FORBIDDEN, "본인의 결제수단만 사용할 수 있습니다."), // 403
    INVALID_TERMS(HttpStatus.BAD_REQUEST, "유효하지 않은 약관이 포함되어 있습니다.");


    private final HttpStatus status; // 이 에러가 나갈 때의 HTTP 상태코드
    private final String message; // 사용자에게 보여줄 메시지

}
