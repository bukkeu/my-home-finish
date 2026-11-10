package com.wanted.moneybeetle.domain.auth.config;

import com.wanted.moneybeetle.domain.auth.model.service.SocialLoginService;
import com.wanted.moneybeetle.domain.auth.model.service.SocialProfileExtractor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;

/* comment.
*   담당 : DBA ② (회원·인증)
*   본인 확인용 다시 로그인(API 8 purpose=reauth)에서만 제공자에게 로그인을 다시 요구한다 (이슈 #9)
*   - 제공자의 로그인 세션이 살아 있으면 비밀번호 입력 없이 인증이 끝나 버려서, 자리를 비운 사람의 브라우저로 탈퇴 확인을 통과할 수 있다.
*     그래서 본인 확인 중인 요청에만 prompt 값을 붙여 제공자가 다시 로그인(또는 계정 선택·동의)을 요구하게 한다. 일반 간편 로그인은 그대로다.
*   - 카카오: prompt=login (카카오계정 로그인을 다시 요구). 카카오톡 인앱 브라우저는 이 값을 지원하지 않을 수 있다.
*   - 구글: 구글이 문서화한 prompt 값은 select_account·consent 이고 login 은 없다. 그래서 계정 선택과 동의 화면을 다시 거치게
*     (select_account consent) 해서, 사용자가 직접 한 번 더 누르게 한다. 비밀번호 입력까지 강제하지는 못한다.
*  */
public class ReauthAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    static final String PROMPT = "prompt";

    private final OAuth2AuthorizationRequestResolver delegate;

    public ReauthAuthorizationRequestResolver(ClientRegistrationRepository clientRegistrations) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(clientRegistrations, "/oauth2/authorization");
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return customize(delegate.resolve(request), request);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return customize(delegate.resolve(request, clientRegistrationId), request);
    }

    private OAuth2AuthorizationRequest customize(OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request) {
        if (authorizationRequest == null || !SocialLoginService.hasPendingReauth(request.getSession(false))) {
            return authorizationRequest;
        }
        String registrationId = authorizationRequest.getAttribute(OAuth2ParameterNames.REGISTRATION_ID);
        String prompt = promptFor(registrationId);
        if (prompt == null) {
            return authorizationRequest;
        }
        return OAuth2AuthorizationRequest.from(authorizationRequest)
                .additionalParameters(parameters -> parameters.put(PROMPT, prompt))
                .build();
    }

    static String promptFor(String registrationId) {
        if (SocialProfileExtractor.REGISTRATION_KAKAO.equals(registrationId)) {
            return "login";
        }
        if (SocialProfileExtractor.REGISTRATION_GOOGLE.equals(registrationId)) {
            return "select_account consent";
        }
        return null;
    }
}
