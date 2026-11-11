package com.wanted.moneybeetle.domain.auth.config;

import com.wanted.moneybeetle.domain.auth.model.service.SocialProfileExtractor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

/* comment.
*   담당 : DBA ② (회원·인증)
*   구글 간편 로그인 설정 (USR-07, 이슈 #9, 환경변수 문서 간편 로그인). (#260)
*   - 클라이언트 ID·시크릿은 환경변수(GOOGLE_CLIENT_ID/SECRET)로만 받는다. 코드·application.yaml 에 값을 쓰지 않는다.
*   - **ID 와 시크릿이 모두 있을 때만 등록한다. 하나라도 비어 있으면 이 설정 전체가 꺼지고(ClientRegistrationRepository 빈이 없음) SecurityConfig 도
*     OAuth 로그인을 켜지 않아서 서버는 그대로 뜬다.** 값이 비어 있는 채로 등록하면 서버가 뜨지 않기 때문이다 (환경변수 문서 간편 로그인).
*   - 구글은 OIDC(openid)를 쓰지 않고 scope profile·email 로 사용자 정보 주소(userinfo)에서 sub·email·email_verified·name 을 받는다.
*   - 리다이렉트 주소는 {baseUrl}/login/oauth2/code/{registrationId} 이다. 구글 개발자 콘솔에 이 주소(로컬: http://localhost:8080/login/oauth2/code/google)를 등록해야 한다.
*  */
@Configuration
public class OAuthClientConfig {

    @Bean
    @ConditionalOnExpression("!'${GOOGLE_CLIENT_ID:}'.isBlank() and !'${GOOGLE_CLIENT_SECRET:}'.isBlank()")
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${GOOGLE_CLIENT_ID:}") String googleId,
            @Value("${GOOGLE_CLIENT_SECRET:}") String googleSecret) {
        return new InMemoryClientRegistrationRepository(google(googleId, googleSecret));
    }

    static ClientRegistration google(String clientId, String clientSecret) {
        return CommonOAuth2Provider.GOOGLE.getBuilder(SocialProfileExtractor.REGISTRATION_GOOGLE)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .scope("profile", "email")
                .build();
    }
}
