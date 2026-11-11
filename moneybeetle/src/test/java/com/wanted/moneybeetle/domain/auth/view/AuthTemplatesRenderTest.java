package com.wanted.moneybeetle.domain.auth.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/* comment.
*   담당 : DBA ② (회원·인증 화면, 이슈 #50)
*   로그인·회원가입·간편 가입·이메일 찾기·비밀번호 재설정 화면(Thymeleaf 템플릿)이 실제로 그려지는지 서버·DB 없이 확인한다.
*   - 템플릿에 문법 오류나 없는 조각 이름이 있으면 화면을 열 때 500 이 나므로, 각 템플릿을 실제 Thymeleaf 엔진으로 그려 본다.
*   - 간편 로그인 버튼은 제공자 키가 있을 때만(모델 값 googleOAuthEnabled) 보이는지 본다.
*   - static/js/auth.js 가 byId('...') 로 찾는 요소가 템플릿에 모두 있는지 확인한다 (id 를 바꾸다 어긋나면 화면이 조용히 동작하지 않는다).
*   - 화면 경로를 연결하는 컨트롤러(AuthPageController, #230)는 이 테스트와 무관하다. 여기서는 확인용 컨트롤러로 템플릿 이름만 연결한다.
*  */
class AuthTemplatesRenderTest {

    @Controller
    static class TestPages {
        @GetMapping("/t/login")
        String login(@RequestParam(defaultValue = "false") boolean google, Model model) {
            model.addAttribute("googleOAuthEnabled", google);
            return "auth/login";
        }

        @GetMapping("/t/signup")
        String signup(@RequestParam(defaultValue = "false") boolean google, Model model) {
            model.addAttribute("googleOAuthEnabled", google);
            return "member/signup";
        }

        @GetMapping("/t/social-signup")
        String socialSignup() {
            return "member/social-signup";
        }

        @GetMapping("/t/find-email")
        String findEmail() {
            return "auth/find-email";
        }

        @GetMapping("/t/password-reset")
        String passwordReset() {
            return "auth/password-reset";
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        ThymeleafViewResolver viewResolver = new ThymeleafViewResolver();
        viewResolver.setTemplateEngine(engine);
        viewResolver.setCharacterEncoding("UTF-8");
        mockMvc = MockMvcBuilders.standaloneSetup(new TestPages()).setViewResolvers(viewResolver).build();
    }

    private String render(String path, String... params) throws Exception {
        var request = get(path);
        for (int i = 0; i + 1 < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static String read(String path) throws IOException {
        try (InputStream in = AuthTemplatesRenderTest.class.getResourceAsStream(path)) {
            assertThat(in).as("클래스패스에 " + path + " 가 있어야 한다").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("로그인 화면이 그려지고, 이메일 찾기·비밀번호 재설정·회원가입 링크가 있다")
    void loginRenders() throws Exception {
        String html = render("/t/login");

        assertThat(html).contains("data-auth-page=\"login\"", "id=\"login-form\"", "id=\"login-email\"", "id=\"login-password\"")
                .contains("/login?view=find-email", "/password-reset", "/signup");
    }

    @Test
    @DisplayName("로그인 화면의 간편 로그인 버튼은 키가 있는 제공자만 보인다")
    void loginSocialButtonsFollowProviderFlags() throws Exception {
        String none = render("/t/login");
        assertThat(none).doesNotContain("/api/v1/auth/oauth/google")
                .doesNotContain("또는 간편하게 로그인").doesNotContain("auth-social");   // 빈 구분선·소셜 묶음도 없다

        String googleOnly = render("/t/login", "google", "true");
        assertThat(googleOnly).contains("/api/v1/auth/oauth/google", "또는 간편하게 로그인");
    }

    @Test
    @DisplayName("회원가입 화면은 4단계(이메일 인증·계정 정보·약관·프로필)로 그려지고 청약통장 안내가 있다")
    void signupRenders() throws Exception {
        String html = render("/t/signup");

        assertThat(html).contains("data-auth-page=\"signup\"", "data-signup-step=\"1\"", "data-signup-step=\"2\"",
                "data-signup-step=\"3\"", "data-signup-step=\"4\"", "id=\"request-code\"", "id=\"verify-code\"", "청약통장");
    }

    @Test
    @DisplayName("회원가입 화면의 간편 시작 버튼도 키가 있는 제공자만 보이고, 하나도 없으면 구분선·묶음 자체가 없다")
    void signupSocialButtonsFollowProviderFlags() throws Exception {
        String none = render("/t/signup");
        assertThat(none).doesNotContain("/api/v1/auth/oauth/").doesNotContain("또는 간편하게 시작하기");

        String googleOnly = render("/t/signup", "google", "true");
        assertThat(googleOnly).contains("또는 간편하게 시작하기", "/api/v1/auth/oauth/google");
    }

    @Test
    @DisplayName("간편 가입·이메일 찾기·비밀번호 재설정 화면이 그려진다 (각자 auth.js 가 구분하는 data-auth-page 값을 가진다)")
    void otherPagesRender() throws Exception {
        assertThat(render("/t/social-signup")).contains("data-auth-page=\"social-signup\"");
        assertThat(render("/t/find-email")).contains("data-auth-page=\"find-email\"");
        assertThat(render("/t/password-reset")).contains("data-auth-page=\"password-reset\"");
    }

    @Test
    @DisplayName("auth.js 의 화면별 블록(if (page === '...'))이 byId 로 찾는 id 가 그 화면을 그린 결과에 모두 있다")
    void everyElementIdUsedByEachPageExistsInThatPage() throws Exception {
        String lf = read("/static/js/auth.js").replace("\r\n", "\n");
        assertEveryScriptIdExists(lf);                                  // LF 로 받은 파일
        assertEveryScriptIdExists(lf.replace("\n", "\r\n"));   // Windows 에서 받아 CRLF 가 된 파일
    }

    private void assertEveryScriptIdExists(String rawScript) throws Exception {
        // 줄 끝이 CRLF 면 정규식의 \n 과 맞지 않으므로 LF 로 맞춘 뒤 찾는다
        String script = rawScript.replace("\r\n", "\n");
        String[][] pages = {{"login", "/t/login"}, {"signup", "/t/signup"}, {"social-signup", "/t/social-signup"},
                {"find-email", "/t/find-email"}, {"password-reset", "/t/password-reset"}};
        for (String[] page : pages) {
            // 화면 블록은 들여쓰기 2칸의 "if (page === 'x') {" 로 시작해 들여쓰기 2칸의 "}" 로 끝난다 (같은 화면의 블록이 둘 이상일 수 있다)
            Matcher block = Pattern.compile("(?s)\\n  if \\(page === '" + Pattern.quote(page[0]) + "'\\) \\{\\n(.*?)\\n  \\}\\n").matcher(script);
            Set<String> wanted = new LinkedHashSet<>();
            while (block.find()) {
                Matcher ids = Pattern.compile("byId\\('([^']+)'\\)").matcher(block.group(1));
                while (ids.find()) {
                    wanted.add(ids.group(1));
                }
            }
            String html = render(page[1]);

            assertThat(wanted).as(page[0] + " 화면 블록에서 byId 로 찾는 id").isNotEmpty();
            for (String id : wanted) {
                assertThat(html).as(page[0] + " 화면에 id=\"" + id + "\" 가 있어야 한다").contains("id=\"" + id + "\"");
            }
        }
    }

    @Test
    @DisplayName("auth.js 가 구분하는 화면 값(page)이 템플릿의 data-auth-page 와 같은 이름이다")
    void pageNamesMatchScript() throws Exception {
        String script = read("/static/js/auth.js");
        for (String page : new String[] {"login", "signup", "social-signup", "find-email", "password-reset"}) {
            assertThat(script).as("auth.js 에 page === '" + page + "' 분기가 있어야 한다").contains("page === '" + page + "'");
        }
    }
}
