package com.wanted.moneybeetle.domain.auth.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanted.moneybeetle.domain.auth.model.service.AuthService;
import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.global.common.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthFailHandler implements AuthenticationFailureHandler {

    private final AuthService authService;
    private final ObjectMapper objectMapper;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {

        MemberErrorCode errorCode = MemberErrorCode.LOGIN_FAILED;

        if (exception instanceof LockedException) {
            errorCode = MemberErrorCode.LOGIN_LOCKED;

        } else if (exception instanceof DisabledException) {
            if (MemberErrorCode.ACCOUNT_SUSPENDED.getCode()
                    .equals(exception.getMessage())) {
                errorCode = MemberErrorCode.ACCOUNT_SUSPENDED;
            } else if (MemberErrorCode.ACCOUNT_WITHDRAWN.getCode()
                    .equals(exception.getMessage())) {
                errorCode = MemberErrorCode.ACCOUNT_WITHDRAWN;
            }

        } else if (exception instanceof BadCredentialsException) {
            String email = request.getParameter("email");

            boolean locked = authService.recordLoginFailure(email);
            if (locked) {
                errorCode = MemberErrorCode.LOGIN_LOCKED;
            }
        }

        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        objectMapper.writeValue(
                response.getWriter(),
                ApiResponse.fail(errorCode)
        );
    }
}
