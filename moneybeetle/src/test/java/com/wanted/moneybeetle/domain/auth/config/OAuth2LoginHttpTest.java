package com.wanted.moneybeetle.domain.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wanted.moneybeetle.support.MockSecurityServices;
import com.wanted.moneybeetle.support.SecurityTestApp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/* comment.
*   담당 : DBA ② (회원·인증)
*   실제 SecurityConfig 에 구글 키(환경변수)가 있을 때 간편 로그인이 켜지는지 확인한다 (이슈 #9)
*   - 제공자 서버에는 가지 않는다: 인증 시작 주소가 구글 로그인 화면 주소로 보내는 것(302)까지만 본다.
*   - 인증이 끝난 뒤의 처리(OAuth2SuccessHandler)는 OAuth2SuccessHandlerTest 가 가짜 인증 정보로 확인한다.
*   - 키가 없는 경우(서버가 그대로 뜨고 OAuth 주소가 열리지 않음)는 SecurityConfigTest 등 기존 테스트가 OAuthClientConfig 없이 돌아 확인한다.
*  */
@SpringBootTest(classes = {SecurityTestApp.class, OAuthClientConfig.class},
        properties = {"GOOGLE_CLIENT_ID=test-google-id", "GOOGLE_CLIENT_SECRET=test-google-secret"})
@MockSecurityServices
class OAuth2LoginHttpTest {

    @Autowired
    private WebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("구글 키가 있으면 로그인 없이 인증 시작 주소가 구글 로그인 화면(client_id·scope·state 포함)으로 보낸다")
    void authorizationStartRedirectsToGoogle() throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String location = result.getResponse().getRedirectedUrl();
        assertThat(location).startsWith("https://accounts.google.com/o/oauth2/v2/auth");
        assertThat(location).contains("client_id=test-google-id").contains("state=").contains("scope=profile%20email");
        assertThat(location).doesNotContain("test-google-secret");   // 시크릿은 주소에 실리지 않는다
    }

    @Test
    @DisplayName("등록하지 않은 제공자(naver)의 인증 시작 주소는 구글로 가지 않는다")
    void unconfiguredProviderIsNotStarted() throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth2/authorization/naver")).andReturn();

        String location = result.getResponse().getRedirectedUrl();
        assertThat(location == null || !location.contains("accounts.google.com")).isTrue();
    }
}
