package com.wanted.moneybeetle.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanted.moneybeetle.global.common.ApiResponse;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import com.wanted.moneybeetle.global.exception.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/* comment.
*   담당 : SCM (공통 기반·보안)
*   Security 필터 단계의 인증·권한 실패 응답 (컨트롤러 전이라 GlobalExceptionHandler 가 못 잡는다)
*   - 로그인 안 함 : API -> 401 UNAUTHORIZED JSON / 화면 -> 로그인 화면으로 이동
*   - 권한 없음   : API -> 403 FORBIDDEN JSON    / 화면 -> error/403.html
*                  단, 학습자 화면(/user/**)을 강사·관리자로 연 경우는 학습자 로그인 화면(/login?next=원래 주소)으로 보낸다 (#415).
*                  학습자 계정으로 로그인하면 next 로 원래 보려던 화면에 간다 (AuthSuccessHandler)
*  */
@Component
@RequiredArgsConstructor
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final String LEARNER_PAGE_PREFIX = "/user/";

    private final ObjectMapper objectMapper;   // 스프링이 만들어 둔 JSON 변환기
    private final AuthenticationEntryPoint loginPage = new LoginUrlAuthenticationEntryPoint("/login");

    // 로그인 안 한 사용자가 로그인이 필요한 주소에 왔을 때
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        if (isApi(request)) {
            writeJson(response, CommonErrorCode.UNAUTHORIZED);
            return;
        }
        loginPage.commence(request, response, authException);
    }

    // 로그인은 했지만 권한(role)이 모자랄 때
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        if (isApi(request)) {
            writeJson(response, CommonErrorCode.FORBIDDEN);
            return;
        }
        if (isLearnerPage(request)) {
            response.sendRedirect(request.getContextPath() + "/login?next=" + URLEncoder.encode(pathWithQuery(request), StandardCharsets.UTF_8));
            return;
        }
        response.sendError(HttpServletResponse.SC_FORBIDDEN);   // -> /error -> templates/error/403.html
    }

    private boolean isApi(HttpServletRequest request) {
        return path(request).startsWith("/api/");
    }

    /* 학습자 화면 (/user/**) */
    private boolean isLearnerPage(HttpServletRequest request) {
        return path(request).startsWith(LEARNER_PAGE_PREFIX);
    }

    /* 앱 안의 경로 (컨텍스트 경로를 뺀 주소) */
    private String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    /* 로그인 뒤 돌아갈 주소 : 경로 + 쿼리 (예: /user/my-page?tab=wrong).
     * 로그인 화면이 이 값으로 바로 이동하므로 컨텍스트 경로를 포함한 주소를 쓴다 (저장된 요청으로 돌아가는 경우와 같게, #418 CodeRabbit) */
    private String pathWithQuery(HttpServletRequest request) {
        String query = request.getQueryString();
        String returnPath = request.getRequestURI();
        return query == null ? returnPath : returnPath + "?" + query;
    }

    private void writeJson(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(errorCode));
    }
}
