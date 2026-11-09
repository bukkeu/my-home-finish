package com.wanted.moneybeetle.domain.auth.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.auth.model.dto.LoginResultDTO;
import com.wanted.moneybeetle.domain.growth.model.dto.AttendanceResultDTO;
import com.wanted.moneybeetle.domain.growth.model.service.AttendanceService;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

/* comment.
*   담당 : DBA ② (회원·인증)
*   로그인 성공 응답 (API 6, 이슈 #6) - 원래 보던 화면 주소를 돌려준다 (DP-11)
*   - 실패 횟수 초기화와 last_login_at 기록은 인증 제공자(LoginAuthenticationProvider)가 같은 트랜잭션 안에서 이미 했다
*   - 응답은 ApiResponse JSON { memberId, nickname, role, redirectUrl } 이고 화면(JS)이 redirectUrl 로 이동한다
*   - 이동할 곳: 로그인 요청의 next 값(사이트 안 경로일 때만) → 로그인 전에 가려던 화면(저장된 요청) → 역할별 첫 화면
*   - 일반 회원(MEMBER)이 로그인하면 growth.AttendanceService 로 오늘 출석을 기록하고(하루 한 번, 연속 5일째부터 캐시 지급)
*     응답에 attendance { checkedToday, newlyChecked, streak, rewardCash } 를 담는다 (GRW-01, 이슈 #12)
*   - 출석 기록이 실패해도 로그인은 성공시킨다. 로그인은 이미 끝났으므로 실패는 서버 로그에만 남기고 attendance 를 비운다(null).
*     출석 기록은 자기 트랜잭션이라 실패해도 로그인 기록과 섞여 되돌려지지 않고, 다음 접속에서 다시 시도된다.
*   - 강사·관리자 로그인에는 출석을 기록하지 않는다 (학습자의 성장 보상이다)
*  */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthSuccessHandler implements AuthenticationSuccessHandler {

    private final ObjectMapper objectMapper;
    private final AttendanceService attendanceService;
    private final RequestCache requestCache = new HttpSessionRequestCache();

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        AuthDetails details = (AuthDetails) authentication.getPrincipal();

        LoginResultDTO result = new LoginResultDTO(details.getMemberId(), details.getNickname(),
                details.getRole(), redirectUrl(request, response, details.getRole()),
                recordAttendance(details));

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.success(result));
    }

    /** 일반 회원이면 오늘 출석을 기록한다. 어떤 이유로 실패해도 로그인은 성공이라 null 을 돌려준다 */
    private AttendanceResultDTO recordAttendance(AuthDetails details) {
        if (!Member.ROLE_MEMBER.equals(details.getRole())) {
            return null;
        }
        try {
            return attendanceService.checkToday(details.getMemberId());
        } catch (RuntimeException e) {
            log.warn("로그인 출석 기록에 실패했어요: memberId={}, {}", details.getMemberId(), e.getClass().getSimpleName());
            return null;
        }
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
