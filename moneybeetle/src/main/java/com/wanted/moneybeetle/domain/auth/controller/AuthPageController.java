package com.wanted.moneybeetle.domain.auth.controller;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 인증·회원가입 화면을 Thymeleaf 템플릿에 연결한다. */
@Controller
public class AuthPageController {

    private static final String GOOGLE_CLIENT_ID = "GOOGLE_CLIENT_ID";
    private static final String GOOGLE_CLIENT_SECRET = "GOOGLE_CLIENT_SECRET";
    private static final String KAKAO_CLIENT_ID = "KAKAO_CLIENT_ID";
    private static final String KAKAO_CLIENT_SECRET = "KAKAO_CLIENT_SECRET";

    private final Environment environment;

    public AuthPageController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping("/login")
    public String login(@RequestParam(name = "view", required = false) String view, Model model) {
        if ("find-email".equals(view)) {
            return "auth/find-email";
        }
        model.addAttribute("googleOAuthEnabled", isProviderEnabled(GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET));
        model.addAttribute("kakaoOAuthEnabled", isProviderEnabled(KAKAO_CLIENT_ID, KAKAO_CLIENT_SECRET));
        return "auth/login";
    }

    @GetMapping("/signup")
    public String signup() {
        return "member/signup";
    }

    @GetMapping("/signup/social")
    public String socialSignup() {
        return "member/social-signup";
    }

    @GetMapping("/password-reset")
    public String passwordReset() {
        return "auth/password-reset";
    }

    private boolean isProviderEnabled(String clientIdKey, String clientSecretKey) {
        return hasValue(clientIdKey) && hasValue(clientSecretKey);
    }

    private boolean hasValue(String propertyName) {
        String value = environment.getProperty(propertyName);
        return value != null && !value.isBlank();
    }
}
