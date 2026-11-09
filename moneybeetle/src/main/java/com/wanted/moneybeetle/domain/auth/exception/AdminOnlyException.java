package com.wanted.moneybeetle.domain.auth.exception;

import org.springframework.security.core.AuthenticationException;

/* comment.
*   담당 : DBA ② (회원·인증)
*   관리자 로그인 주소에 관리자가 아닌 계정이 로그인하려 했을 때 (API 83, 이슈 #33). 응답은 403 FORBIDDEN 이다.
*   비밀번호가 맞은 뒤에 던지므로, 일반 회원·강사 계정의 로그인 성공 기록(마지막 로그인 시각 등)은 남지 않는다.
*  */
public class AdminOnlyException extends AuthenticationException {

    public AdminOnlyException(String message) {
        super(message);
    }
}
