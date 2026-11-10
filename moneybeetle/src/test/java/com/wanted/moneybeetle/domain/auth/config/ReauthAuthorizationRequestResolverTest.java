package com.wanted.moneybeetle.domain.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanted.moneybeetle.domain.auth.model.service.SocialLoginService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/* comment.
*   담당 : DBA ② (회원·인증)
*   본인 확인용 다시 로그인에서만 제공자에게 로그인을 다시 요구하는지 확인한다 (이슈 #9). 외부 서버 없이 돈다
*  */
class ReauthAuthorizationRequestResolverTest {

    private ReauthAuthorizationRequestResolver resolver;
    private final MockHttpSession session = new MockHttpSession();

    @BeforeEach
    void setUp() {
        resolver = new ReauthAuthorizationRequestResolver(new InMemoryClientRegistrationRepository(
                OAuthClientConfig.google("g-id", "g-secret"), OAuthClientConfig.kakao("k-id", "k-secret")));
    }

    private MockHttpServletRequest request(String provider) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/" + provider);
        request.setServletPath("/oauth2/authorization/" + provider);
        request.setSession(session);
        return request;
    }

    /** 세션에 넣는 값(Reauth 레코드)은 서비스 패키지 안이라 서비스의 beginReauth 로 만든다 */
    private void markReauthPending() {
        new SocialLoginService(null, null, null, null, Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"), ZoneId.of("Asia/Seoul")))
                .beginReauth(7L, session);
    }

    @Test
    @DisplayName("일반 간편 로그인에는 prompt 를 붙이지 않는다")
    void normalLoginHasNoPrompt() {
        assertThat(resolver.resolve(request("kakao")).getAdditionalParameters()).doesNotContainKey("prompt");
        assertThat(resolver.resolve(request("google")).getAdditionalParameters()).doesNotContainKey("prompt");
    }

    @Test
    @DisplayName("본인 확인 중인 카카오 요청에는 prompt=login 을 붙인다")
    void kakaoReauthForcesLogin() {
        markReauthPending();

        OAuth2AuthorizationRequest authorization = resolver.resolve(request("kakao"));

        assertThat(authorization.getAdditionalParameters()).containsEntry("prompt", "login");
        assertThat(authorization.getAuthorizationRequestUri()).contains("prompt=login");
    }

    @Test
    @DisplayName("본인 확인 중인 구글 요청에는 계정 선택·동의를 다시 거치는 prompt 를 붙인다")
    void googleReauthForcesAccountSelection() {
        markReauthPending();

        OAuth2AuthorizationRequest authorization = resolver.resolve(request("google"));

        assertThat(authorization.getAdditionalParameters()).containsEntry("prompt", "select_account consent");
    }

    @Test
    @DisplayName("등록 ID 를 직접 넘기는 호출과 알 수 없는 제공자·인증 시작 주소가 아닌 요청도 안전하다")
    void explicitRegistrationIdAndUnknown() {
        markReauthPending();

        assertThat(resolver.resolve(request("kakao"), "kakao").getAdditionalParameters()).containsEntry("prompt", "login");
        assertThat(ReauthAuthorizationRequestResolver.promptFor("naver")).isNull();
        assertThat(resolver.resolve(new MockHttpServletRequest("GET", "/other"))).isNull();
    }
}
