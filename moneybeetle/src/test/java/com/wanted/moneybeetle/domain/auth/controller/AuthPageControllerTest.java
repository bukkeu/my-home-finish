package com.wanted.moneybeetle.domain.auth.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.ui.ExtendedModelMap;

class AuthPageControllerTest {

    @Test
    void loginShowsOnlyConfiguredOAuthProviders() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GOOGLE_CLIENT_ID", "google-id")
                .withProperty("GOOGLE_CLIENT_SECRET", "google-secret")
                .withProperty("KAKAO_CLIENT_ID", "kakao-id");
        AuthPageController controller = new AuthPageController(environment);
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.login(null, model);

        assertEquals("auth/login", view);
        assertTrue((Boolean) model.asMap().get("googleOAuthEnabled"));
        assertFalse((Boolean) model.asMap().get("kakaoOAuthEnabled"));
    }

    @Test
    void findEmailUsesPublicLoginRoute() {
        AuthPageController controller = new AuthPageController(new MockEnvironment());

        assertEquals("auth/find-email", controller.login("find-email", new ExtendedModelMap()));
    }

    @Test
    void pagesResolveToTheirThymeleafTemplates() {
        AuthPageController controller = new AuthPageController(new MockEnvironment());

        assertEquals("member/signup", controller.signup());
        assertEquals("member/social-signup", controller.socialSignup());
        assertEquals("auth/password-reset", controller.passwordReset());
    }
}
