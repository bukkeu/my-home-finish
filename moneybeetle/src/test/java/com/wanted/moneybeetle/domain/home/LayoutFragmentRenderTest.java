package com.wanted.moneybeetle.domain.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.wanted.moneybeetle.domain.home.model.dto.LayoutDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.WebApplicationContext;

/* comment.
*   담당 : DBA ② (홈)
*   공통 레이아웃 조각(header · sidebar · notice)이 실제로 그려지는지 확인한다.
*   서버 시작 클래스(#2)와 DB 없이 돌도록, 이 테스트 안에서만 쓰는 작은 설정으로 Thymeleaf + Security 만 띄운다.
*   test/resources/templates/layout-preview.html 이 세 조각을 한 화면에 모아 그린다.
*  */
@SpringBootTest(classes = {LayoutFragmentRenderTest.TestApp.class, LayoutFragmentRenderTest.PreviewController.class})
class LayoutFragmentRenderTest {

    @Configuration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class})
    static class TestApp {
        /** 로그인하지 않은 요청도 화면까지 오게 연다 (권한별로 무엇이 보이는지만 본다) */
        @Bean
        SecurityFilterChain chain(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(a -> a.anyRequest().permitAll()).build();
        }
    }

    @Controller
    static class PreviewController {
        @GetMapping("/preview")
        String preview(Model model, @RequestParam(defaultValue = "5") int level,
                       @RequestParam(defaultValue = "true") boolean withLayout) {
            if (withLayout) {
                model.addAttribute("layout", new LayoutDTO("민지", level, "원룸", 12400, 75, 150, 500, 3, "2일 더 출석하면 캐시", 8, 12));
            }
            return "layout-preview";
        }
    }

    @Autowired
    private WebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String render(String authority, String query) throws Exception {
        var request = get("/preview" + query);
        if (authority != null) {
            request = request.with(user("tester").authorities(new SimpleGrantedAuthority(authority)));
        }
        String html = mockMvc.perform(request).andReturn().getResponse().getContentAsString();
        dumpIfRequested(authority, query, html);
        return html;
    }

    /**
     * 눈으로 확인하고 싶을 때만: 환경변수를 명령 앞에 설정해 돌리면(Git Bash: LAYOUT_DUMP=build/layout-preview ./gradlew test --rerun-tasks,
     * PowerShell: $env:LAYOUT_DUMP='build/layout-preview'; ./gradlew test --rerun-tasks)
     * 환경변수는 Gradle 의 입력이 아니라서 --rerun-tasks 가 없으면 test 가 UP-TO-DATE 로 건너뛰어 파일이 만들어지지 않는다.
     * 그려진 HTML 을 파일로 남긴다 (static 폴더와 함께 간단한 웹 서버로 열어 보면 된다). 평소 테스트에는 영향이 없다.
     */
    private void dumpIfRequested(String authority, String query, String html) throws Exception {
        String dir = System.getenv("LAYOUT_DUMP");
        if (dir == null) {
            return;
        }
        java.nio.file.Path out = java.nio.file.Path.of(dir);
        java.nio.file.Files.createDirectories(out);
        String name = (authority == null ? "anonymous" : authority.toLowerCase()) + (query.contains("level=12") ? "-lv12" : "")
                + (query.contains("withLayout=false") ? "-nolayout" : "") + ".html";
        java.nio.file.Files.writeString(out.resolve(name), html);
    }

    @Test
    @DisplayName("학습자: 헤더·메뉴·프로필이 보이고, 레벨이 모자란 트레이닝은 잠금과 필요 레벨이 표시된다")
    void memberSeesLockedTraining() throws Exception {
        String html = render("MEMBER", "?level=5");

        assertThat(html).contains("오늘의 경제단어", "민지", "12,400", "연속 출석 3일", "mb-notice");
        assertThat(html).contains("data-mb-lock=\"8\"", "data-mb-lock=\"12\"", "Lv.8부터", "Lv.12부터");
        assertThat(html).contains("/css/common.css", "/js/drawer.js", "MB_SERVER_CONFIG");
        assertThat(html).doesNotContain("강사 센터", "관리자 · 전체 권한");
    }

    @Test
    @DisplayName("로그아웃 폼은 API 명세서 7의 POST /api/v1/auth/logout 으로 보낸다 (역할 3종 모두)")
    void logoutFormAction() throws Exception {
        for (String authority : new String[] {"MEMBER", "INSTRUCTOR", "ADMIN"}) {
            String html = render(authority, "");

            assertThat(html).as(authority).contains("action=\"/api/v1/auth/logout\"");
            assertThat(html).as(authority).doesNotContain("action=\"/logout\"");
        }
    }

    @Test
    @DisplayName("CSRF 토큰이 meta 태그로 내려와서 MB.api 가 PATCH·POST 요청에 붙일 수 있다")
    void csrfMetaTags() throws Exception {
        // 같은 요청에서 Spring Security 가 만든 토큰·헤더 이름과 화면의 meta 값이 같은지 본다
        var result = mockMvc.perform(get("/preview?level=5")
                .with(user("tester").authorities(new SimpleGrantedAuthority("MEMBER")))).andReturn();
        String html = result.getResponse().getContentAsString();
        CsrfToken csrf = (CsrfToken) result.getRequest().getAttribute("_csrf");

        assertThat(csrf).isNotNull();
        assertThat(csrf.getToken()).isNotBlank();
        assertThat(html).contains("<meta name=\"_csrf\" content=\"" + csrf.getToken() + "\">");
        assertThat(html).contains("<meta name=\"_csrf_header\" content=\"" + csrf.getHeaderName() + "\">");
    }

    @Test
    @DisplayName("학습자: 레벨이 충분하면 잠금 표시가 사라진다")
    void memberUnlocked() throws Exception {
        String html = render("MEMBER", "?level=12");

        assertThat(html).contains("모의 투자", "모의 청약");
        assertThat(html).doesNotContain("data-mb-lock", "Lv.8부터", "Lv.12부터");
    }

    @Test
    @DisplayName("강사: 강사 메뉴가 보이고 학습자 헤더(검색·경제단어·캐시)는 보이지 않는다")
    void instructorMenu() throws Exception {
        String html = render("INSTRUCTOR", "");

        assertThat(html).contains("강사 센터", "← 수강생 화면으로", "Q&amp;A 관리");
        assertThat(html).doesNotContain("오늘의 경제단어", "mb-cash", "관리자 · 전체 권한");
    }

    @Test
    @DisplayName("강사: 메뉴에 퀴즈 관리·요약글 관리 링크가 있고, 순서는 대시보드 · 강의 관리 · 강의 생성 · 퀴즈 관리 · 요약글 관리 · Q&A 관리 · 마이페이지 (#366)")
    void instructorMenuHasQuizAndSummaryLinks() throws Exception {
        String html = render("INSTRUCTOR", "");

        assertThat(html).contains("/instructor/dashboard?tab=quizzes", "/instructor/dashboard?tab=summaries");
        int[] positions = {html.indexOf(">대시보드</a>"), html.indexOf(">강의 관리</a>"), html.indexOf(">강의 생성</a>"),
                html.indexOf(">퀴즈 관리</a>"), html.indexOf(">요약글 관리</a>"), html.indexOf(">Q&amp;A 관리</a>"),
                html.indexOf(">마이페이지</a>")};
        assertThat(positions).isSorted();
        assertThat(positions[0]).isNotNegative();
    }

    @Test
    @DisplayName("학습자·관리자 메뉴에는 강사 전용 퀴즈 관리·요약글 관리가 보이지 않는다")
    void quizAndSummaryLinksAreInstructorOnly() throws Exception {
        assertThat(render("MEMBER", "")).doesNotContain("퀴즈 관리", "요약글 관리");
        assertThat(render("ADMIN", "")).doesNotContain("요약글 관리");
    }

    @Test
    @DisplayName("관리자: 고정 사이드바와 운영 메뉴가 보인다")
    void adminMenu() throws Exception {
        String html = render("ADMIN", "");

        assertThat(html).contains("관리자 · 전체 권한", "data-mb-pinned", "모의투자", "mb-hamburger--admin");
        assertThat(html).doesNotContain("오늘의 경제단어", "강사 센터");
    }

    @Test
    @DisplayName("layout 값이 없어도(null) 화면이 깨지지 않고 기본값으로 그려진다")
    void rendersWithoutLayoutAttribute() throws Exception {
        String html = render("MEMBER", "?withLayout=false");

        assertThat(html).contains("오늘의 경제단어", "mb-notice", "본문 영역");
    }

    @Test
    @DisplayName("로그인하지 않으면 헤더·메뉴는 그려지지 않고 본문만 보인다")
    void anonymousSeesNoMenu() throws Exception {
        String html = render(null, "");

        assertThat(html).contains("본문 영역");
        assertThat(html).doesNotContain("mb-header", "mb-drawer", "mb-admin-side", "오늘의 경제단어");
    }

    @Test
    @DisplayName("LayoutDTO: 닉네임 첫 글자와 경험치 막대 비율 계산")
    void layoutDtoHelpers() {
        LayoutDTO dto = new LayoutDTO("민지", 5, "원룸", 0, 75, 150, 0, 0, "", 8, 12);
        assertThat(dto.getInitial()).isEqualTo("민");
        assertThat(dto.getExpPercent()).isEqualTo(50);
        assertThat(new LayoutDTO(" ", 1, "", 0, 0, 150, 0, 0, "", 8, 12).getInitial()).isEqualTo("?");
        assertThat(new LayoutDTO("a", 1, "", 0, 999, 150, 0, 0, "", 8, 12).getExpPercent()).isEqualTo(100);
    }
}
