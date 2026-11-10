package com.wanted.moneybeetle.domain.home.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.home.model.dto.LayoutDTO;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.support.MockSecurityServices;
import com.wanted.moneybeetle.support.SecurityTestApp;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.WebApplicationContext;

/* comment.
*   담당 : DBA ② (홈 화면, 이슈 #51)
*   서비스 소개 메인(index.html)과 홈 화면(home/home.html)이 실제 SecurityConfig·공통 레이아웃 조각과 함께 그려지는지 DB 없이 확인한다.
*   - 템플릿에 문법 오류나 없는 조각 이름이 있으면 화면을 열 때 500 이 나므로 실제 Thymeleaf 엔진으로 그려 본다.
*   - 화면 주소를 연결하는 컨트롤러(#51 후속 BE 작업)는 이 테스트와 무관하다. 여기서는 확인용 컨트롤러로 템플릿 이름만 연결한다.
*   - static/js/home.js 가 byId('...') 로 찾는 요소가 home.html 에 모두 있는지 확인한다 (id 를 바꾸다 어긋나면 화면이 조용히 동작하지 않는다).
*  */
@SpringBootTest(classes = {SecurityTestApp.class, HomeTemplatesRenderTest.TestPages.class})
@MockSecurityServices
class HomeTemplatesRenderTest {

    @Controller
    static class TestPages {
        @GetMapping("/")
        String index() {
            return "index";
        }

        @GetMapping("/user/home")
        String home(@RequestParam(defaultValue = "false") boolean withLayout, Model model) {
            if (withLayout) {
                model.addAttribute("layout", new LayoutDTO("민지", 4, "원룸", 12400L, 90, 150, 5000L, 4, "", 8, 12));
            }
            return "home/home";
        }
    }

    @Autowired
    private MemberService memberService;
    @Autowired
    private WebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(memberService.isActiveMember(anyLong())).thenReturn(true);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private AuthDetails member() {
        AuthDetails details = mock(AuthDetails.class);
        when(details.getMemberId()).thenReturn(8L);
        when(details.getUsername()).thenReturn("minji@moneybeetle.local");
        doReturn(List.of(new SimpleGrantedAuthority("MEMBER"))).when(details).getAuthorities();
        return details;
    }

    private static String read(String path) throws IOException {
        try (InputStream in = HomeTemplatesRenderTest.class.getResourceAsStream(path)) {
            assertThat(in).as("클래스패스에 " + path + " 가 있어야 한다").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("서비스 소개 메인은 로그인하지 않아도 열리고, 회원가입·로그인 링크와 로드맵이 있다")
    void indexRendersForAnonymous() throws Exception {
        String html = mockMvc.perform(get("/")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("내 집 마련,", "연습부터.", "href=\"/login\"", "href=\"/signup\"", "id=\"road\"",
                "/css/landing.css", "/js/landing.js", "내 집까지 가는 길", "/images/house-top-hd.png")
                .doesNotContain("내 홈으로 가기");   // 로그인하지 않은 사람에게는 "무료로 시작하기"만 보인다
        assertThat(html).contains("무료로 시작하기");
    }

    @Test
    @DisplayName("서비스 소개 메인에서 로그인한 회원에게는 로그인·회원가입 대신 내 홈으로 가는 링크가 보인다")
    void indexRendersForMember() throws Exception {
        String html = mockMvc.perform(get("/").with(user(member()))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("/user/home", "내 홈으로 가기").doesNotContain("무료로 시작하기").doesNotContain("href=\"/signup\"");
    }

    @Test
    @DisplayName("홈 화면은 로그인한 회원에게 그려지고, 레이아웃(layout)의 레벨·칭호·닉네임이 들어간다")
    void homeRendersWithLayout() throws Exception {
        String html = mockMvc.perform(get("/user/home").param("withLayout", "true").with(user(member())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("id=\"hm\"", "data-mb-home", "data-level=\"4\"", "data-tier=\"원룸\"", "민지", "님, Money Beetle에", "님, 오늘도 한 걸음",
                "/css/home.css", "/js/home.js", "/js/words.js", "data-mb-words", "id=\"hm-training\"");
    }

    @Test
    @DisplayName("layout 이 없어도 홈 화면은 기본값으로 그려진다 (닉네임 '회원', 레벨 1)")
    void homeRendersWithoutLayout() throws Exception {
        String html = mockMvc.perform(get("/user/home").with(user(member())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("data-level=\"1\"", "data-tier=\"고시원\"", "회원");
    }

    @Test
    @DisplayName("홈 화면은 로그인하지 않으면 로그인 화면으로 보낸다")
    void homeRequiresLogin() throws Exception {
        mockMvc.perform(get("/user/home")).andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("home.js 가 byId 로 찾는 요소(id)가 홈 화면 템플릿에 모두 있다")
    void everyElementIdUsedByHomeScriptExists() throws Exception {
        String script = read("/static/js/home.js");
        String template = read("/templates/home/home.html");
        Set<String> wanted = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("byId\\('([^']+)'\\)").matcher(script);
        while (matcher.find()) {
            wanted.add(matcher.group(1));
        }

        assertThat(wanted).as("home.js 에서 byId 로 찾는 id").isNotEmpty();
        for (String id : wanted) {
            assertThat(template).as("홈 화면에 id=\"" + id + "\" 가 있어야 한다").contains("id=\"" + id + "\"");
        }
    }

    @Test
    @DisplayName("화면이 불러오는 스타일·스크립트·이미지 파일이 모두 있다")
    void staticFilesExist() throws Exception {
        for (String path : new String[] {"/static/css/home.css", "/static/css/landing.css", "/static/js/home.js", "/static/js/landing.js",
                "/static/js/words.js", "/static/images/house-top-hd.png", "/static/images/beetle-mascot.png"}) {
            assertThat(HomeTemplatesRenderTest.class.getResource(path)).as(path).isNotNull();
        }
    }
}
