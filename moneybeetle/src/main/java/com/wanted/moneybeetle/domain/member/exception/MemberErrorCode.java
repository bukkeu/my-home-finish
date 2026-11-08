package com.wanted.moneybeetle.domain.member.exception;

import com.wanted.moneybeetle.global.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/* comment.
*   담당 : DBA ② (회원·인증)
*   회원 도메인 에러 코드 (API 명세서 4장). 사용자에게 보이는 문구는 정책 정의서 6장 기준이다.
*   global.exception.ErrorCode 는 이슈 #2 에서 getCode() / getStatus() / getMessage() 로 정해졌고,
*   아래 필드 이름과 Lombok @Getter 가 같은 이름의 메서드를 만들어 그대로 맞는다.
*  */
@Getter
public enum MemberErrorCode implements ErrorCode {

    EMAIL_DUPLICATED("EMAIL_DUPLICATED", HttpStatus.CONFLICT, "이미 가입된 이메일이에요."),
    NICKNAME_DUPLICATED("NICKNAME_DUPLICATED", HttpStatus.CONFLICT, "이미 사용 중인 닉네임이에요."),
    NICKNAME_INVALID("NICKNAME_INVALID", HttpStatus.BAD_REQUEST, "닉네임은 2~12자의 한글·영문·숫자만 쓸 수 있어요."),
    PASSWORD_MISMATCH("PASSWORD_MISMATCH", HttpStatus.BAD_REQUEST, "비밀번호가 서로 달라요."),
    PASSWORD_INVALID("PASSWORD_INVALID", HttpStatus.BAD_REQUEST, "비밀번호는 8~20자의 영문과 숫자를 모두 포함해야 해요."),
    REQUIRED_TERMS_NOT_AGREED("REQUIRED_TERMS_NOT_AGREED", HttpStatus.BAD_REQUEST, "필수 약관에 동의해 주세요."),
    AGE_UNDER_14("AGE_UNDER_14", HttpStatus.BAD_REQUEST, "만 14세 이상만 가입할 수 있어요."),
    TERMS_NOT_FOUND("TERMS_NOT_FOUND", HttpStatus.BAD_REQUEST, "존재하지 않는 약관이에요."),
    MEMBER_NOT_FOUND("MEMBER_NOT_FOUND", HttpStatus.NOT_FOUND, "존재하지 않는 회원이에요.");

    private final String code;
    private final HttpStatus status;
    private final String message;

    MemberErrorCode(String code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }
}
