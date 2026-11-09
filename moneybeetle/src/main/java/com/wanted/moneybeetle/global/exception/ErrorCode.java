package com.wanted.moneybeetle.global.exception;

/* comment.
*   담당 : SCM (공통 기반·보안)
*   에러 코드 공통 규약 (code, httpStatus, message) : 에러 신고서 양식
*   각 도메인은 이 인터페이스를 구현한 enum 을 자기 패키지에 만든다 -> 한 파일을 여럿이 고치지 않게 (충돌 방지)
*   예) member.exception.MemberErrorCode, trading.exception.TradingErrorCode
*  */


import org.springframework.http.HttpStatus;

public interface ErrorCode {
    String getCode();          // 에러 코드 이름

    HttpStatus getStatus();    // HTTP 상태

    String getMessage();       // 화면에 보여 줄 문구
}

