package com.wanted.moneybeetle.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.wanted.moneybeetle.domain.auth.model.service.SocialLoginService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ExtendedModelMap;

/* comment.
*   담당 : DBA ② (회원·인증 화면, 이슈 #230)
*   AuthPageController - 화면 이름과 간편 로그인 제공자 상태(Model 값)를 확인한다. 서버·DB 없이 돈다.
*   템플릿이 실제로 그려지는지는 AuthTemplatesRenderTest(#50)가 확인한다.
*  */
class AuthPageControllerTest {

    private final SocialLoginService socialLoginService = mock(SocialLoginService.class);
    private AuthPageController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        controller = new AuthPageController(socialLoginService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("로그인 화면은 등록된 제공자만 버튼을 보이도록 Model 에 담는다 (구글만 등록)")
    void loginShowsOnlyAvailableProviders() {
        when(socialLoginService.isProviderAvailable("google")).thenReturn(true);
        when(socialLoginService.isProviderAvailable("kakao")).thenReturn(false);
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.login(null, model);

        assertThat(view).isEqualTo("auth/login");
        assertThat(model.asMap().get("googleOAuthEnabled")).isEqualTo(true);
        assertThat(model.asMap().get("kakaoOAuthEnabled")).isEqualTo(false);
    }

    @Test
    @DisplayName("회원가입 화면도 같은 기준으로 제공자 상태를 담는다 (둘 다 등록)")
    void signupShowsProviderFlags() {
        when(socialLoginService.isProviderAvailable("google")).thenReturn(true);
        when(socialLoginService.isProviderAvailable("kakao")).thenReturn(true);
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.signup(model);

        assertThat(view).isEqualTo("member/signup");
        assertThat(model.asMap().get("googleOAuthEnabled")).isEqualTo(true);
        assertThat(model.asMap().get("kakaoOAuthEnabled")).isEqualTo(true);
    }

    @Test
    @DisplayName("제공자가 하나도 등록되지 않았으면 둘 다 false 다 (서비스 mock 기본값)")
    void noProviders() {
        ExtendedModelMap model = new ExtendedModelMap();

        controller.login(null, model);

        assertThat(model.asMap().get("googleOAuthEnabled")).isEqualTo(false);
        assertThat(model.asMap().get("kakaoOAuthEnabled")).isEqualTo(false);
    }

    @Test
    @DisplayName("/login?view=find-email 은 이메일 찾기 화면이고, 이때는 제공자 상태를 조회하지 않는다")
    void findEmailView() {
        ExtendedModelMap model = new ExtendedModelMap();

        assertThat(controller.login("find-email", model)).isEqualTo("auth/find-email");
        verify(socialLoginService, never()).isProviderAvailable(org.mockito.ArgumentMatchers.anyString());
        assertThat(model.asMap()).isEmpty();
    }

    @Test
    @DisplayName("주소가 템플릿 이름에 연결된다: /login, /signup, /signup/social, /password-reset")
    void routesResolveToTemplates() throws Exception {
        mockMvc.perform(get("/login")).andExpect(status().isOk()).andExpect(view().name("auth/login"))
                .andExpect(model().attributeExists("googleOAuthEnabled", "kakaoOAuthEnabled"));
        mockMvc.perform(get("/login").param("view", "find-email")).andExpect(view().name("auth/find-email"));
        mockMvc.perform(get("/signup")).andExpect(view().name("member/signup"))
                .andExpect(model().attributeExists("googleOAuthEnabled", "kakaoOAuthEnabled"));
        mockMvc.perform(get("/signup/social")).andExpect(view().name("member/social-signup"));
        mockMvc.perform(get("/password-reset")).andExpect(view().name("auth/password-reset"));
    }
}
