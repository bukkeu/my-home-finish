package com.wanted.moneybeetle.domain.member.exception;

import com.wanted.moneybeetle.global.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/* comment.
*   담당 : DBA ② (회원·인증)
*   회원 도메인 에러 코드 (API 명세서 4장). 사용자에게 보이는 문구는 정책 정의서 3-2 기준이다.
*   global.exception.ErrorCode 는 이슈 #2 에서 getCode() / getStatus() / getMessage() 로 정해졌고,
*   아래 필드 이름과 Lombok @Getter 가 같은 이름의 메서드를 만들어 그대로 맞는다.
*  */
@Getter
public enum MemberErrorCode implements ErrorCode {

    EMAIL_DUPLICATED("EMAIL_DUPLICATED", HttpStatus.CONFLICT, "이미 가입된 이메일이에요."),
    EMAIL_INVALID("EMAIL_INVALID", HttpStatus.BAD_REQUEST, "올바른 이메일 형식을 입력해 주세요."),
    NICKNAME_DUPLICATED("NICKNAME_DUPLICATED", HttpStatus.CONFLICT, "이미 사용 중인 닉네임이에요."),
    NICKNAME_INVALID("NICKNAME_INVALID", HttpStatus.BAD_REQUEST, "닉네임은 2~12자의 한글·영문·숫자만 쓸 수 있어요."),
    PASSWORD_MISMATCH("PASSWORD_MISMATCH", HttpStatus.BAD_REQUEST, "비밀번호가 서로 달라요."),
    PASSWORD_INVALID("PASSWORD_INVALID", HttpStatus.BAD_REQUEST, "비밀번호는 8~20자의 영문과 숫자를 모두 포함해야 해요."),
    REQUIRED_TERMS_NOT_AGREED("REQUIRED_TERMS_NOT_AGREED", HttpStatus.BAD_REQUEST, "필수 약관에 동의해 주세요."),
    AGE_UNDER_14("AGE_UNDER_14", HttpStatus.BAD_REQUEST, "만 14세 이상만 가입할 수 있어요."),
    BIRTH_DATE_INVALID("BIRTH_DATE_INVALID", HttpStatus.BAD_REQUEST, "올바른 생년월일을 입력해 주세요."),
    TERMS_NOT_FOUND("TERMS_NOT_FOUND", HttpStatus.BAD_REQUEST, "존재하지 않는 약관이에요."),
    MEMBER_NOT_FOUND("MEMBER_NOT_FOUND", HttpStatus.NOT_FOUND, "존재하지 않는 회원이에요."),

    // 로그인 (API 6). 문구는 API 명세서 4장·정책 정의서 3-2 기준
    LOGIN_FAILED("LOGIN_FAILED", HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호를 확인해 주세요."),
    LOGIN_LOCKED("LOGIN_LOCKED", HttpStatus.LOCKED, "로그인 시도가 너무 많아요. 잠시 뒤에 다시 시도해 주세요."),
    ACCOUNT_SUSPENDED("ACCOUNT_SUSPENDED", HttpStatus.FORBIDDEN, "이용이 제한된 계정이에요."),
    ACCOUNT_WITHDRAWN("ACCOUNT_WITHDRAWN", HttpStatus.FORBIDDEN, "탈퇴한 계정이에요."),

    // 비밀번호 재설정·변경, 탈퇴 (API 11, 16, 17)
    PASSWORD_WRONG("PASSWORD_WRONG", HttpStatus.BAD_REQUEST, "현재 비밀번호가 맞지 않아요."),
    TOKEN_EXPIRED("TOKEN_EXPIRED", HttpStatus.BAD_REQUEST, "인증이 만료되었거나 올바르지 않아요. 다시 요청해 주세요."),
    TOKEN_USED("TOKEN_USED", HttpStatus.BAD_REQUEST, "이미 사용한 인증이에요. 다시 요청해 주세요."),

    // 가입 중 본인인증 (API 4, 5, 5-1)
    IDENTITY_NOT_VERIFIED("IDENTITY_NOT_VERIFIED", HttpStatus.BAD_REQUEST, "본인인증을 먼저 해 주세요."),
    IDENTITY_CODE_INVALID("IDENTITY_CODE_INVALID", HttpStatus.BAD_REQUEST, "인증번호가 맞지 않아요. 다시 확인해 주세요."),
    IDENTITY_CODE_EXPIRED("IDENTITY_CODE_EXPIRED", HttpStatus.BAD_REQUEST, "인증번호가 만료되었거나 너무 많이 틀렸어요. 인증번호를 다시 받아 주세요."),
    IDENTITY_CODE_TOO_SOON("IDENTITY_CODE_TOO_SOON", HttpStatus.TOO_MANY_REQUESTS, "인증번호는 1분에 한 번만 받을 수 있어요. 잠시 뒤에 다시 시도해 주세요."),
    IDENTITY_MAIL_UNAVAILABLE("IDENTITY_MAIL_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, "지금은 인증 메일을 보낼 수 없어요. 잠시 뒤에 다시 시도해 주세요."),

    // 간편 로그인 (API 8, 8-1, 8-2)
    OAUTH_FAILED("OAUTH_FAILED", HttpStatus.BAD_GATEWAY, "간편 로그인에 실패했어요. 다시 시도하거나 이메일로 로그인해 주세요."),
    OAUTH_PROVIDER_UNAVAILABLE("OAUTH_PROVIDER_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, "지금은 이 간편 로그인을 쓸 수 없어요. 이메일로 로그인해 주세요."),
    OAUTH_SESSION_EXPIRED("OAUTH_SESSION_EXPIRED", HttpStatus.BAD_REQUEST, "간편 로그인 정보가 만료됐어요. 처음부터 다시 시도해 주세요.");

    private final String code;
    private final HttpStatus status;
    private final String message;

    MemberErrorCode(String code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }
}
