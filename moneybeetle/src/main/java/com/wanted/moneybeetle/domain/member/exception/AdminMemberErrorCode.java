package com.wanted.moneybeetle.domain.member.exception;

import com.wanted.moneybeetle.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/* comment.
*   담당 : DBA ① (사용자·강사 관리, #37)
*   관리자 회원·강사 관리 API 101~105 오류. MemberErrorCode(DBA ②)와 따로 둬서 회원가입·로그인 작업과 충돌하지 않게 한다
*  */
@Getter
@RequiredArgsConstructor
public enum AdminMemberErrorCode implements ErrorCode {
    REASON_REQUIRED(HttpStatus.BAD_REQUEST, "정지 사유를 입력해 주세요"),                              // API 102 (SP-06)
    MEMBER_NOT_CHANGEABLE(HttpStatus.CONFLICT, "관리자 계정이나 탈퇴한 계정은 바꿀 수 없어요"),        // API 102·103·105
    APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "강사 가입 신청을 찾을 수 없어요"),                   // API 105
    APPLICATION_NOT_PENDING(HttpStatus.CONFLICT, "승인 대기 중인 신청만 승인할 수 있어요");            // API 105

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
