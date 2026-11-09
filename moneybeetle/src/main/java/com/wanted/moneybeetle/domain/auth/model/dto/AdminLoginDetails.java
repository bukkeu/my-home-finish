package com.wanted.moneybeetle.domain.auth.model.dto;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

/* comment.
*   담당 : DBA ② (회원·인증)
*   관리자 로그인 요청 표시 (API 83, 이슈 #33)
*   - 관리자 로그인 주소(/api/v1/admin/auth/login)로 들어온 로그인 시도의 인증 정보(details)에 붙는 표시다.
*   - 일반 로그인과 같은 인증 제공자(LoginAuthenticationProvider)를 쓰되, 이 표시가 있으면 비밀번호가 맞아도 ADMIN 권한이 아니면 막는다.
*  */
public class AdminLoginDetails extends WebAuthenticationDetails {

    public AdminLoginDetails(HttpServletRequest request) {
        super(request);
    }
}
