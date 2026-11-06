package com.wanted.moneybeetle.global.exception;

/* comment.
*   담당 : SCM (회원·공통)
*   에러 코드 공통 규약 (code, httpStatus, message)
*   각 도메인은 이 인터페이스를 구현한 enum 을 자기 패키지에 만든다 -> 한 파일을 여럿이 고치지 않게 (충돌 방지)
*   예) member.exception.MemberErrorCode, trading.exception.TradingErrorCode
*  */
public interface ErrorCode {

}
