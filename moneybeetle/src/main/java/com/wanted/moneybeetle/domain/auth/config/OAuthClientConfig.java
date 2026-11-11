package com.wanted.moneybeetle.domain.auth.config;

import com.wanted.moneybeetle.domain.auth.model.service.SocialProfileExtractor;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/* comment.
*   담당 : DBA ② (회원·인증)
*   구글·카카오 간편 로그인 설정 (USR-07, 이슈 #9, 환경변수 문서 간편 로그인)
*   - 클라이언트 ID·시크릿은 환경변수(GOOGLE_CLIENT_ID/SECRET, KAKAO_CLIENT_ID/SECRET)로만 받는다. 코드·application.yaml 에 값을 쓰지 않는다.
*   - **ID 와 시크릿이 모두 있는 제공자만 등록한다. 하나라도 비어 있으면 그 제공자는 등록하지 않는다.** 둘 다 없으면 이 설정 전체가 꺼지고(ClientRegistrationRepository 빈이 없음) SecurityConfig 도
*     OAuth 로그인을 켜지 않아서 서버는 그대로 뜬다. 값이 비어 있는 채로 등록하면 서버가 뜨지 않기 때문이다 (환경변수 문서 간편 로그인).
*   - 구글은 OIDC(openid)를 쓰지 않고 scope profile·email 로 사용자 정보 주소(userinfo)에서 sub·email·email_verified·name 을 받는다.
*   - 카카오는 스프링이 기본 제공하지 않는 제공자라 주소를 직접 적는다. scope: profile_nickname, account_email. 사용자 ID 속성은 id.
*   - 리다이렉트 주소는 {baseUrl}/login/oauth2/code/{registrationId} 이다. 구글·카카오 개발자 콘솔에 이 주소(로컬: http://localhost:8080/login/oauth2/code/google, .../kakao)를 등록해야 한다.
*  */
@Configuration
public class OAuthClientConfig {

    private static final String REDIRECT_URI = "{baseUrl}/login/oauth2/code/{registrationId}";

    @Bean
    @ConditionalOnExpression("(!'${GOOGLE_CLIENT_ID:}'.isBlank() and !'${GOOGLE_CLIENT_SECRET:}'.isBlank())"
            + " or (!'${KAKAO_CLIENT_ID:}'.isBlank() and !'${KAKAO_CLIENT_SECRET:}'.isBlank())")
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${GOOGLE_CLIENT_ID:}") String googleId,
            @Value("${GOOGLE_CLIENT_SECRET:}") String googleSecret,
            @Value("${KAKAO_CLIENT_ID:}") String kakaoId,
            @Value("${KAKAO_CLIENT_SECRET:}") String kakaoSecret) {
        List<ClientRegistration> registrations = new ArrayList<>();
        if (!googleId.isBlank() && !googleSecret.isBlank()) {
            registrations.add(google(googleId, googleSecret));
        }
        if (!kakaoId.isBlank() && !kakaoSecret.isBlank()) {
            registrations.add(kakao(kakaoId, kakaoSecret));
        }
        return new InMemoryClientRegistrationRepository(registrations);
    }

    static ClientRegistration google(String clientId, String clientSecret) {
        return CommonOAuth2Provider.GOOGLE.getBuilder(SocialProfileExtractor.REGISTRATION_GOOGLE)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .scope("profile", "email")
                .build();
    }

    static ClientRegistration kakao(String clientId, String clientSecret) {
        return ClientRegistration.withRegistrationId(SocialProfileExtractor.REGISTRATION_KAKAO)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(REDIRECT_URI)
                .scope("profile_nickname", "account_email")
                .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                .tokenUri("https://kauth.kakao.com/oauth/token")
                .userInfoUri("https://kapi.kakao.com/v2/user/me")
                .userNameAttributeName("id")
                .clientName("Kakao")
                .build();
    }
}
