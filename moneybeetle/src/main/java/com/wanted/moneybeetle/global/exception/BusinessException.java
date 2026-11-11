package com.wanted.moneybeetle.global.exception;

/* comment.
*   담당 : SCM (공통 기반·보안)
*   ErrorCode 를 담아서 던지는 업무 예외 (RuntimeException) : 문제 생겼어!! 신고서 들고 던지는 예외
*   - 화면 문구는 기본으로 ErrorCode 의 문구. 운영 설정 값처럼 바뀌는 숫자를 문구에 넣어야 할 때만 message 를 따로 준다 (#327)
*     코드(code)·HTTP 상태는 그대로이고, 응답의 error.message 만 이 문구가 된다 (GlobalExceptionHandler)
*  */

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException{
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 화면 문구만 바꾼다 (예: 운영 설정의 최소 납입액을 넣은 "최소 30,000 캐시부터 …"). 코드·상태는 errorCode 그대로 */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

}
