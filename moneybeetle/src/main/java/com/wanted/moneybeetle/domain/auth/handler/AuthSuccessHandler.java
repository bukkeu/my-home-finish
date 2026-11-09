package com.wanted.moneybeetle.domain.auth.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.auth.model.dto.LoginResultDTO;
import com.wanted.moneybeetle.domain.auth.model.service.AuthService;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

/* comment.
*   담당 : DBA ② (회원·인증)
*   로그인 성공 처리 (API 6, 이슈 #6) - 실패 횟수 초기화, last_login_at 기록, 원래 보던 화면 주소를 돌려준다 (DP-11)
*   - 응답은 ApiResponse JSON { memberId, nickname, role, redirectUrl } 이고 화면(JS)이 redirectUrl 로 이동한다
*   - 이동할 곳: 로그인 요청의 next 값(사이트 안 경로일 때만) → 로그인 전에 가려던 화면(저장된 요청) → 역할별 첫 화면
*   - TODO(#12): 오늘 첫 접속이면 growth.AttendanceService 로 출석을 기록하고 응답에 attendance 를 담는다 (GRW-01)
*  */
@Component
@RequiredArgsConstructor
public class AuthSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final ObjectMapper objectMapper;
    private final RequestCache requestCache = new HttpSessionRequestCache();

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        AuthDetails details = (AuthDetails) authentication.getPrincipal();
        authService.recordLoginSuccess(details.getMemberId());

        LoginResultDTO result = new LoginResultDTO(details.getMemberId(), details.getNickname(),
                details.getRole(), redirectUrl(request, response, details.getRole()));

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.success(result));
    }

    private String redirectUrl(HttpServletRequest request, HttpServletResponse response, String role) {
        String next = request.getParameter("next");
        if (isSitePath(next)) {
            return next;
        }
        SavedRequest saved = requestCache.getRequest(request, response);
        if (saved != null) {
            requestCache.removeRequest(request, response);
            String path = toPath(saved.getRedirectUrl());
            if (isSitePath(path) && !path.startsWith("/api/")) {
                return path;
            }
        }
        return defaultHome(role);
    }

    /** 역할별 첫 화면 (화면설계의 URL) */
    static String defaultHome(String role) {
        if (Member.ROLE_ADMIN.equals(role)) {
            return "/admin/dashboard";
        }
        if (Member.ROLE_INSTRUCTOR.equals(role)) {
            return "/instructor/dashboard";
        }
        return "/user/home";
    }

    /** 사이트 안 경로인가. 바깥 주소로 보내는 우회(//, 역슬래시, 탭·줄바꿈 등 제어 문자)는 거절한다 */
    static boolean isSitePath(String path) {
        if (path == null || !path.startsWith("/") || path.startsWith("//") || path.indexOf('\\') >= 0) {
            return false;
        }
        return path.chars().noneMatch(c -> c <= 0x1F || c == 0x7F);
    }

    private static String toPath(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
