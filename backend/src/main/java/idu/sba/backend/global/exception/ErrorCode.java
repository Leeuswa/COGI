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
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."),                    // 404

    //AST 컨텍스트 추출
    AST_PARSE_ERROR(HttpStatus.BAD_REQUEST,"코드를 파싱할 수 없습니다."),

    //레포 팀원 초대(REV-006)
    REPO_NOT_FOUND(HttpStatus.NOT_FOUND,"레포지토리를 찾을 수 없습니다."),
    NOT_REPO_MEMBER(HttpStatus.FORBIDDEN,"레포 팀원이 아닙니다."),
    INSUFFICIENT_REPO_PERMISSION(HttpStatus.FORBIDDEN,"해당 작업을 수행할 권한이 없습니다."),
    INVITATION_NOT_FOUND(HttpStatus.NOT_FOUND,"초대를 찾을 수 없습니다."),
    INVITATION_ALREADY_RESPONDED(HttpStatus.CONFLICT,"이미 응답한 초대입니다."),
    ALREADY_REPO_MEMBER(HttpStatus.CONFLICT,"이미 레포에 소속된 사용자입니다."),
    INVITATION_GITHUB_USERNAME_MISMATCH(HttpStatus.FORBIDDEN,"초대받은 GitHub 계정으로만 응답할 수 있습니다."),
    GITHUB_USER_NOT_FOUND(HttpStatus.BAD_REQUEST,"GitHub를 연동하지 않았거나 사이트에 가입하지 않은 사용자입니다."),

    //GitHub 레포 연동(API-022/023)
    GITHUB_NOT_LINKED(HttpStatus.BAD_REQUEST,"GitHub 계정이 연동되어 있지 않습니다."),
    GITHUB_API_ERROR(HttpStatus.BAD_GATEWAY,"GitHub API 호출에 실패했습니다."),
    GITHUB_REPO_NOT_FOUND(HttpStatus.NOT_FOUND,"GitHub에서 해당 레포지토리를 찾을 수 없습니다."),
    REPO_ALREADY_LINKED(HttpStatus.CONFLICT,"이미 연동된 레포지토리입니다.");

    private final HttpStatus status; // 이 에러가 나갈 때의 HTTP 상태코드
    private final String message; // 사용자에게 보여줄 메시지

}
