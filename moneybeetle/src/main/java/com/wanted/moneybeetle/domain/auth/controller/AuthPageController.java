package com.wanted.moneybeetle.domain.auth.controller;

import com.wanted.moneybeetle.domain.auth.model.service.SocialLoginService;
import com.wanted.moneybeetle.domain.auth.model.service.SocialProfileExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/* comment.
*   담당 : DBA ② (회원·인증 화면, 이슈 #230)
*   인증·회원가입 화면(Thymeleaf 템플릿)을 주소에 연결한다. 화면 구현은 #50(FE), 동작하는 API 는 #9.
*   - GET /login          : auth/login (?view=find-email 이면 auth/find-email = 이메일 찾기 화면)
*   - GET /signup         : member/signup (이메일 인증 4단계 가입)
*   - GET /signup/social  : member/social-signup (간편 로그인으로 처음 온 사람의 추가 정보 입력, API 8-1·8-2)
*   - GET /password-reset : auth/password-reset (메일의 ?token= 으로 새 비밀번호 입력)
*   - Model 값 googleOAuthEnabled : 로그인·회원가입 화면이 구글 간편 로그인 버튼을 보일지 정한다.
*     API 8 과 같은 기준(SocialLoginService.isProviderAvailable: 지원하는 제공자이고 ID·시크릿으로 등록됨)이라 키가 없으면 버튼이 보이지 않는다.
*   - 이 주소들은 SecurityConfig 에서 로그인 없이 열려 있다 (/signup/social 은 간편 로그인 가입 중인 세션이 쓴다).
*  */
@Controller
@RequiredArgsConstructor
public class AuthPageController {

    private final SocialLoginService socialLoginService;

    @GetMapping("/login")
    public String login(@RequestParam(name = "view", required = false) String view, Model model) {
        if ("find-email".equals(view)) {
            return "auth/find-email";
        }
        addProviderFlags(model);
        return "auth/login";
    }

    @GetMapping("/signup")
    public String signup(Model model) {
        addProviderFlags(model);
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

    private void addProviderFlags(Model model) {
        model.addAttribute("googleOAuthEnabled", socialLoginService.isProviderAvailable(SocialProfileExtractor.REGISTRATION_GOOGLE));
    }
}
