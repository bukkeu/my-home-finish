package com.wanted.moneybeetle.domain.auth.handler;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.member.model.dto.LoginMemberDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/* comment.
*   담당 : DBA ② (회원·인증)
*   이메일·비밀번호 로그인과 같은 모양(AuthDetails)의 로그인 상태를 세션에 직접 만든다 (간편 로그인, 이슈 #9)
*   - 구글 인증이 끝나면 Spring Security 의 인증 정보는 OAuth2 사용자(OAuth2AuthenticationToken)인데, 이 서비스의 나머지 코드는
*     로그인한 회원을 AuthDetails 로 읽는다(@AuthenticationPrincipal, 권한 MEMBER·INSTRUCTOR·ADMIN). 그래서 같은 모양으로 바꿔 넣는다.
*   - 세션 ID 는 새로 발급한다 (세션 고정 보호). 가입을 이어서 하는 중 세션에 담아 둔 값은 그대로 따라간다.
*  */
@Component
public class AuthSessionWriter {

    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public void signIn(HttpServletRequest request, HttpServletResponse response, LoginMemberDTO member) {
        AuthDetails details = new AuthDetails(member);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities()));
        SecurityContextHolder.setContext(context);
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        contextRepository.saveContext(context, request, response);
    }

    /** 로그인 상태를 지운다. 간편 로그인으로 막 인증됐지만 아직 가입하지 않은 사람(또는 실패한 사람)이 로그인된 것으로 남지 않게 한다 */
    public void clear(HttpServletRequest request, HttpServletResponse response) {
        SecurityContext empty = SecurityContextHolder.createEmptyContext();
        SecurityContextHolder.setContext(empty);
        contextRepository.saveContext(empty, request, response);
    }
}
