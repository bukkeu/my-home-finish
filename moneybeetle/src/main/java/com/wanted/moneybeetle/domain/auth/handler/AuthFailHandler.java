package com.wanted.moneybeetle.domain.auth.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanted.moneybeetle.domain.auth.model.service.AdminOnlyException;
import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import com.wanted.moneybeetle.global.exception.ErrorCode;
import com.wanted.moneybeetle.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/* comment.
*   담당 : DBA ② (회원·인증)
*   로그인 실패 응답 (API 6, 이슈 #6)
*   - 관리자 로그인 주소에 관리자가 아닌 계정이 들어오면 403 FORBIDDEN (#33)
*   - 잠금(423 LOGIN_LOCKED)·정지(403)·탈퇴(403)·이메일 또는 비밀번호 오류(401 LOGIN_FAILED)를 ApiResponse JSON 으로 응답한다
*   - 실패 횟수 기록과 잠금(5회 실패 시 1분, 이후 5회마다 5분, DP-10)은 여기서 하지 않는다. 인증 제공자
*     (LoginAuthenticationProvider)가 회원 행 잠금 트랜잭션 안에서 이미 끝냈고, 이 핸들러는 그 결과를 응답으로 바꾸기만 한다
*   - AuthService 가 던진 잠금·정지·탈퇴 예외는 Spring Security 가 InternalAuthenticationServiceException 으로 감싸서 보내므로
*     원인(cause)을 따라가며 찾는다
*  */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthFailHandler implements AuthenticationFailureHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        ErrorCode errorCode = resolve(exception);

        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(errorCode));
    }

    private ErrorCode resolve(AuthenticationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof AdminOnlyException) {
                return CommonErrorCode.FORBIDDEN;   // 관리자 로그인 주소에 관리자가 아닌 계정 (API 83)
            }
            if (cause instanceof LockedException) {
                return MemberErrorCode.LOGIN_LOCKED;
            }
            if (cause instanceof DisabledException) {
                return MemberErrorCode.ACCOUNT_WITHDRAWN.getCode().equals(cause.getMessage())
                        ? MemberErrorCode.ACCOUNT_WITHDRAWN
                        : MemberErrorCode.ACCOUNT_SUSPENDED;
            }
            cause = cause.getCause();
        }

        if (!(exception instanceof BadCredentialsException)) {
            // 그 밖의 인증 오류(DB 오류 등)는 이유를 로그로만 남기고 사용자에게는 일반 실패로 알린다
            log.warn("로그인 처리 중 예상하지 못한 인증 오류", exception);
        }
        return MemberErrorCode.LOGIN_FAILED;
    }
}
