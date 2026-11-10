package com.wanted.moneybeetle.domain.auth.config;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.global.security.SecurityErrorHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/* comment.
*   담당 : DBA ② (회원·인증)
*   로그인해 둔 세션이라도 계정이 탈퇴·정지됐으면 막는다 (SP-06, 이슈 #8 CodeRabbit 지적)
*   - 탈퇴(API 17)는 요청을 보낸 세션만 끝낼 수 있다. 같은 계정으로 다른 기기·브라우저에 열어 둔 세션은 남아 있으므로,
*     로그인한 요청마다 회원 상태를 확인해 정상(ACTIVE) 계정이 아니면 그 세션을 끝내고 로그인이 필요하다고 응답한다 (API 401 JSON / 화면 로그인 이동).
*   - 관리자가 계정을 정지했을 때도 이미 열린 세션이 계속 쓰이지 않게 한다.
*   - 관리자가 강사 권한을 회수했을 때도(API 103, #37) INSTRUCTOR 세션을 끝낸다. 다시 로그인하면 MEMBER 권한으로 들어온다.
*   - 요청마다 회원 1행을 기본키로 읽는다. 로그인하지 않은 요청은 확인하지 않는다.
*   - 스프링이 모든 요청에 자동 등록하지 않도록 @Component 를 붙이지 않고, SecurityConfig 가 Security 필터 체인에만 넣는다.
*  */
public class ActiveMemberFilter extends OncePerRequestFilter {

    private static final String INSTRUCTOR = "INSTRUCTOR";     // AuthDetails.role 값 (ROLE_ 접두사 없음)

    private final MemberService memberService;
    private final SecurityErrorHandler securityErrorHandler;

    public ActiveMemberFilter(MemberService memberService, SecurityErrorHandler securityErrorHandler) {
        this.memberService = memberService;
        this.securityErrorHandler = securityErrorHandler;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthDetails details
                && (!memberService.isActiveMember(details.getMemberId()) || isRevokedInstructor(details))) {
            SecurityContextHolder.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            AccountStatusException reason = new DisabledException("inactive member");
            securityErrorHandler.commence(request, response, reason);
            return;
        }
        chain.doFilter(request, response);
    }

    /* 강사 세션인데 관리자가 강사 권한을 회수했는가 (#37). 강사 세션에만 회원을 한 번 더 읽는다 */
    private boolean isRevokedInstructor(AuthDetails details) {
        return INSTRUCTOR.equals(details.getRole()) && memberService.isInstructorRevoked(details.getMemberId());
    }
}
