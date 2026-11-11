package com.wanted.moneybeetle.domain.auth.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wanted.moneybeetle.support.MockSecurityServices;
import com.wanted.moneybeetle.support.SecurityTestApp;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.context.WebApplicationContext;

/* comment.
*   담당 : DBA ② (관리자 로그인 화면, 이슈 #61)
*   관리자 로그인 화면(auth/admin-login.html)이 실제 SecurityConfig 와 함께 로그인 없이 그려지는지 DB 없이 확인한다.
*   - 확인용 컨트롤러가 템플릿 이름만 연결한다 (실제 주소 연결은 AuthPageController, 그 테스트가 따로 확인한다).
*   - admin-login.js 가 byId('...') 로 찾는 요소가 템플릿에 모두 있는지, 로그인 API 주소와 폼 형식이 맞는지 확인한다.
*  */
@SpringBootTest(classes = {SecurityTestApp.class, AdminLoginTemplateRenderTest.TestPages.class})
@MockSecurityServices
class AdminLoginTemplateRenderTest {

    @Controller
    static class TestPages {
        @GetMapping("/admin/login")
        String adminLogin() {
            return "auth/admin-login";
        }
    }

    @Autowired
    private WebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static String read(String path) throws IOException {
        try (InputStream in = AdminLoginTemplateRenderTest.class.getResourceAsStream(path)) {
            assertThat(in).as("클래스패스에 " + path + " 가 있어야 한다").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("관리자 로그인 화면은 로그인하지 않아도 열리고, 이메일·비밀번호 칸과 안내가 있다")
    void rendersWithoutLogin() throws Exception {
        String html = mockMvc.perform(get("/admin/login")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("관리자 로그인", "id=\"admin-login-form\"", "method=\"post\"", "action=\"/api/v1/admin/auth/login\"", "name=\"_csrf\"", "id=\"admin-email\"", "id=\"admin-pw\"", "id=\"admin-login-alert\"",
                "관리자 화면의 모든 작업은 기록됩니다", "/css/auth.css", "/css/admin-login.css", "/js/admin-login.js", "발급받은 관리자 계정");
    }

    @Test
    @DisplayName("admin-login.js 가 byId 로 찾는 요소(id)가 템플릿에 모두 있다")
    void everyElementIdUsedByScriptExists() throws Exception {
        String script = read("/static/js/admin-login.js");
        String template = read("/templates/auth/admin-login.html");
        Set<String> wanted = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("(?:getElementById)\\('([^']+)'\\)").matcher(script);
        while (matcher.find()) {
            wanted.add(matcher.group(1));
        }
        matcher = Pattern.compile("(?:show|invalid)\\([^'\\n]*'([a-z-]+-error)'").matcher(script);
        while (matcher.find()) {
            wanted.add(matcher.group(1));
        }

        assertThat(wanted).as("admin-login.js 가 찾는 id").isNotEmpty();
        for (String id : wanted) {
            assertThat(template).as("템플릿에 id=\"" + id + "\" 가 있어야 한다").contains("id=\"" + id + "\"");
        }
    }

    @Test
    @DisplayName("스크립트는 관리자 로그인 API(83)에 폼 형식으로 보내고, 돌아갈 곳은 주소를 풀어 본 뒤 같은 사이트의 /admin/ 아래 주소만 쓴다")
    void scriptUsesFormLoginAndSafeNext() throws Exception {
        String script = read("/static/js/admin-login.js");

        assertThat(script).contains("/api/v1/admin/auth/login", "application/x-www-form-urlencoded", "URLSearchParams", "_csrf",
                "new URL(next, window.location.origin)", "path.indexOf('/admin/') === 0", "/admin/dashboard", "LOGIN_LOCKED", "FORBIDDEN");
    }
}
