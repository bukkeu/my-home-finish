package com.wanted.moneybeetle.domain.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/* comment.
*   담당 : DBA ② (회원·인증)
*   OAuthClientConfig (이슈 #9) - 구글 키(환경변수)가 모두 있을 때만 등록되고, 없으면 설정 전체가 꺼지는지 확인한다. 외부 서버 없이 돈다
*  */
class OAuthClientConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(OAuthClientConfig.class)
            // 개발 환경·CI 의 OAuth 환경변수가 테스트에 영향을 주지 않게 기본값을 비워 두고, 각 테스트가 필요한 값만 덮어쓴다
            .withPropertyValues("GOOGLE_CLIENT_ID=", "GOOGLE_CLIENT_SECRET=");

    @Test
    @DisplayName("키가 하나도 없으면 등록 정보(빈)를 만들지 않는다 (서버는 그대로 뜬다)")
    void noKeysNoBean() {
        runner.run(context -> assertThat(context).doesNotHaveBean(ClientRegistrationRepository.class));
    }

    @Test
    @DisplayName("ID 가 공백이면 없는 것으로 본다")
    void blankIdIsIgnored() {
        runner.withPropertyValues("GOOGLE_CLIENT_ID= ")
                .run(context -> assertThat(context).doesNotHaveBean(ClientRegistrationRepository.class));
    }

    @Test
    @DisplayName("ID 만 있고 시크릿이 없거나 공백이면 등록하지 않는다")
    void secretMissingOrBlankIsIgnored() {
        runner.withPropertyValues("GOOGLE_CLIENT_ID=g")
                .run(context -> assertThat(context).doesNotHaveBean(ClientRegistrationRepository.class));
        runner.withPropertyValues("GOOGLE_CLIENT_ID=g", "GOOGLE_CLIENT_SECRET= ")
                .run(context -> assertThat(context).doesNotHaveBean(ClientRegistrationRepository.class));
    }

    @Test
    @DisplayName("구글 키가 모두 있으면 구글만 등록한다 (scope profile·email, 사용자 ID 속성 sub)")
    void googleOnly() {
        runner.withPropertyValues("GOOGLE_CLIENT_ID=google-id", "GOOGLE_CLIENT_SECRET=google-secret").run(context -> {
            ClientRegistrationRepository repository = context.getBean(ClientRegistrationRepository.class);
            ClientRegistration google = repository.findByRegistrationId("google");
            assertThat(google).isNotNull();
            assertThat(google.getClientId()).isEqualTo("google-id");
            assertThat(google.getScopes()).containsExactlyInAnyOrder("profile", "email");
            assertThat(google.getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName()).isEqualTo("sub");
            assertThat(repository.findByRegistrationId("naver")).isNull();   // 구글 말고는 등록하지 않는다
        });
    }
}
