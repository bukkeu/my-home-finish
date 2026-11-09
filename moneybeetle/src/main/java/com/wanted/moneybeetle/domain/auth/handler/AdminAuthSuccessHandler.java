package com.wanted.moneybeetle.domain.auth.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanted.moneybeetle.domain.auth.model.dto.AdminLoginResultDTO;
import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/* comment.
*   담당 : DBA ② (회원·인증)
*   관리자 로그인 성공 응답 (API 83, 이슈 #33) { memberId, role: "ADMIN" }
*   - 관리자가 아닌 계정은 인증 제공자가 이미 막았으므로 여기에는 ADMIN 만 온다.
*   - 일반 로그인 성공 응답(AuthSuccessHandler)과 달리 원래 보던 화면 주소는 돌려주지 않는다. 관리자 화면(JS)이 대시보드로 이동한다.
*  */
@Component
@RequiredArgsConstructor
public class AdminAuthSuccessHandler implements AuthenticationSuccessHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        AuthDetails details = (AuthDetails) authentication.getPrincipal();

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(),
                ApiResponse.success(new AdminLoginResultDTO(details.getMemberId(), details.getRole())));
    }
}
