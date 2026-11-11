package com.wanted.moneybeetle.domain.auth.handler;

import com.wanted.moneybeetle.domain.auth.model.service.SocialLoginService;
import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/* comment.
*   담당 : DBA ② (회원·인증)
*   간편 로그인 실패 처리 (이슈 #9) - 사용자가 동의 화면에서 취소했거나, 인증 코드·토큰 교환이 실패했거나, 요청이 위조·만료됐을 때 불린다.
*   - 로그인 화면으로 보내며 oauthError=OAUTH_FAILED 로 알린다 (이메일 로그인으로 안내하는 문구는 화면이 오류 코드로 고른다).
*   - 인증 코드는 한 번만 쓸 수 있어 자동으로 다시 시도하지 않는다. 사용자가 처음부터 다시 시도한다.
*   - 로그에는 오류 종류(OAuth2 오류 코드)만 남긴다. 제공자가 돌려준 설명·토큰은 남기지 않는다.
*  */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2FailHandler implements AuthenticationFailureHandler {

    private final SocialLoginService socialLoginService;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String reason = exception instanceof OAuth2AuthenticationException oauth
                ? oauth.getError().getErrorCode()
                : exception.getClass().getSimpleName();
        log.warn("간편 로그인에 실패했어요: {}", reason);
        response.sendRedirect(OAuth2SuccessHandler.LOGIN_ERROR_URL + MemberErrorCode.OAUTH_FAILED.getCode());
    }
}
