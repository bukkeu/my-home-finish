package com.wanted.moneybeetle.domain.member.view;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.context.WebApplicationContext;

/* comment.
*   담당 : DBA ② (마이페이지 설정·성장 탭, 이슈 #69)
*   내가 만든 마이페이지 조각 2개(캐시 내역 성장 탭·설정 탭)가 실제 Thymeleaf 엔진으로 그려지는지 DB 없이 확인한다.
*   - 마이페이지 틀(member/mypage.html, #54)과 주소 연결은 이 테스트와 무관하다. 여기서는 확인용 컨트롤러가 조각을 하나씩 직접 그린다.
*   - 각 조각의 스크립트(static/js)가 byId('...') 로 찾는 요소가 그 조각에 모두 있는지 확인한다 (id 를 바꾸다 어긋나면 화면이 조용히 동작하지 않는다).
*   - 틀·다른 탭과 겹치지 않게 조각 안의 모든 id 가 mg- 로 시작하는지 확인한다 (서수영 안내: 예린 mg-, 서수영 mh-, 신아림 ml-).
*   - 성장 탭에는 청약통장을, 두 탭 모두 프로필 요약·수상 내역·사진 변경을 넣지 않는다 (틀의 프로필 카드가 맡는다 #54, 청약통장은 housing 탭 #70).
*  */
@SpringBootTest(classes = {SecurityTestApp.class, MypageFragmentsRenderTest.TestPages.class})
@MockSecurityServices
class MypageFragmentsRenderTest {

    @Controller
    static class TestPages {
        @GetMapping("/user/t/growth")
        String growth() {
            return "fragments/mypage/growth :: growthTabFragment";
        }

        @GetMapping("/user/t/settings")
        String settings() {
            return "fragments/mypage/settings :: settingsTabFragment";
        }
    }

    /** 조각 이름 / 화면 확인용 주소 / 조각 파일 / 그 조각의 스크립트 */
    private static final String[][] FRAGMENTS = {
            {"growth", "/user/t/growth", "/templates/fragments/mypage/growth.html", "/static/js/mypage-growth.js"},
            {"settings", "/user/t/settings", "/templates/fragments/mypage/settings.html", "/static/js/mypage-settings.js"}};

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

    private String render(String path) throws Exception {
        return mockMvc.perform(get(path).with(user(member()))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static String read(String path) throws IOException {
        try (InputStream in = MypageFragmentsRenderTest.class.getResourceAsStream(path)) {
            assertThat(in).as("클래스패스에 " + path + " 가 있어야 한다").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Set<String> matches(String regex, String text) {
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile(regex).matcher(text);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    @Test
    @DisplayName("성장 탭·설정 탭 조각이 각자 그려지고, 자기 스타일·스크립트를 함께 불러온다")
    void fragmentsRender() throws Exception {
        String growth = render("/user/t/growth");
        String settings = render("/user/t/settings");

        assertThat(growth).contains("id=\"mg-growth\"", "/css/mypage-mg.css", "/js/mypage-growth.js", "캐시 내역", "경험치 내역·레벨 표", "레벨·칭호 표");
        assertThat(settings).contains("id=\"mg-settings\"", "/css/mypage-mg.css", "/js/mypage-settings.js", "개인정보 설정", "비밀번호 변경",
                "id=\"mg-profile-form\"", "id=\"mg-pw-form\"", "id=\"mg-wd\"", "탈퇴하기");
    }

    @Test
    @DisplayName("성장 탭에는 청약통장·수상 내역이, 설정 탭에는 사진 변경이 없다 (틀의 프로필 카드·housing 탭이 맡는다)")
    void notMyScope() throws Exception {
        String growth = render("/user/t/growth");
        String settings = render("/user/t/settings");

        assertThat(growth).doesNotContain("청약통장", "수상 내역", "mg-awards", "mg-level-now");
        assertThat(settings).doesNotContain("mg-photo", "사진 변경", "프로필 이미지");
    }

    @Test
    @DisplayName("각 스크립트가 byId 로 찾는 요소(id)가 그 조각에 모두 있다")
    void everyElementIdUsedByEachScriptExists() throws Exception {
        for (String[] fragment : FRAGMENTS) {
            String html = render(fragment[1]);
            Set<String> wanted = matches("byId\\('([^']+)'\\)", read(fragment[3]));
            assertThat(wanted).as(fragment[0] + " 스크립트가 찾는 id").isNotEmpty();
            for (String id : wanted) {
                assertThat(html).as(fragment[0] + " 조각에 id=\"" + id + "\" 가 있어야 한다").contains("id=\"" + id + "\"");
            }
        }
    }

    @Test
    @DisplayName("조각 안의 모든 id 는 mg- 로 시작한다 (틀·청약통장 mh-·학습 ml- 탭과 겹치지 않는다)")
    void idsUseOwnPrefix() throws Exception {
        for (String[] fragment : FRAGMENTS) {
            for (String id : matches("\\sid=\"([^\"]+)\"", read(fragment[2]))) {
                assertThat(id).as(fragment[0] + " 조각의 id").startsWith("mg-");
            }
        }
    }

    @Test
    @DisplayName("스크립트가 설정 탭 탈퇴 흐름에 쓰는 API 주소(17-1·17-2)와 오류 코드가 들어 있다")
    void withdrawFlowUsesVerificationApi() throws Exception {
        String script = read("/static/js/mypage-settings.js");

        assertThat(script).contains("/api/v1/members/me/withdraw-verification/request", "/api/v1/members/me/withdraw-verification",
                "WITHDRAW_VERIFICATION_REQUIRED", "/api/v1/members/me/password");
        assertThat(script).doesNotContain("/profile-image");
    }

    @Test
    @DisplayName("탈퇴 인증 응답은 보낸 창이 그대로일 때만 탈퇴로 이어지고, 캐시 조회가 실패하면 필터를 막는다")
    void staleResponsesAndFailedCashLoadAreGuarded() throws Exception {
        String settings = read("/static/js/mypage-settings.js");
        String growth = read("/static/js/mypage-growth.js");

        // 안내 요청(1)·인증 성공(2)·인증 오류(3)·비밀번호 확인 거절(4)이 각각 보낸 창과 지금 창을 비교한다
        // 주석(// ...)에 같은 글자가 있어도 세지 않도록 줄 전체 주석을 뺀 코드에서 센다 (JS 를 실행하지 않는 정적 패턴 검사라 보조 수단이다)
        String settingsCode = settings.replaceAll("(?m)^\s*//.*$", "");
        assertThat(settingsCode.split("ession !== dialogSession", -1).length - 1).isGreaterThanOrEqualTo(5);
        assertThat(growth).contains("mg-filter').forEach(function (button) { button.disabled = true; });");
        assertThat(read("/static/css/mypage-mg.css")).contains(".mg .mg-levels { overflow-x: auto; display: block; }");   // 좁은 화면에서 레벨 표가 페이지를 넘치지 않게
        assertThat(read("/templates/fragments/mypage/settings.html")).contains("인증번호 받기를 누르면 가입한 이메일로");   // 인증번호는 탈퇴하기가 아니라 인증번호 단계의 버튼으로 보낸다
        assertThat(growth).contains("레벨 표를 불러오지 못했어요.").doesNotContain("Promise.all([api('/api/v1/members/me'), api('/api/v1/levels')])", "Promise.all([levelsPromise");   // 내 정보와 레벨 표를 따로 불러온다
        assertThat(read("/static/css/mypage-mg.css")).contains("minmax(min(300px, 100%), 1fr)");   // 320px 화면에서 설정 카드가 넘치지 않는다
        assertThat(growth).contains("filter.disabled = false;");            // 더 보기로 내역을 받으면 막아 둔 필터를 푼다
        assertThat(settings).contains("sentGender", "byId('mg-gender').value = me.gender");   // 성별을 비워 저장해도 서버에 남은 값을 보여 준다
    }

    @Test
    @DisplayName("탈퇴 창: 비밀번호가 없는 회원은 서버에 비운 탈퇴를 보내지 않고 바로 인증번호 단계로 가며, 이전 창의 응답은 새 창을 바꾸지 못한다")
    void withdrawDialogGuards() throws Exception {
        String script = read("/static/js/mypage-settings.js");

        // loginProvider 로 비밀번호 없는 회원을 알아보고, 창을 열 때 바로 인증번호 단계로 간다
        assertThat(script).contains("me.loginProvider !== 'EMAIL'").containsPattern("if \\(noPassword\\(\\)\\) enterCodeMode\\(\\);");
        // 비밀번호 확인의 거절 응답은 코드(주석이 아닌) 안에서 창 세션이 같을 때만 단계를 바꾼다
        assertThat(script).containsPattern("withdraw\\(password\\)\\.then\\(done, function \\(error\\) \\{\\s*if \\(passwordSession !== dialogSession\\) return;");
        // 비밀번호를 비운 채로는 비밀번호 단계에서 서버에 탈퇴를 보내지 않고, 내 정보를 받기 전에는 제출하지 못한다
        assertThat(script).containsPattern("if \\(!password\\) return say\\('mg-wd-msg'").contains("submit.disabled = !me;");
        // 비운 탈퇴 요청은 인증번호 확인(17-2)이 이 창에서 방금 성공한 뒤에만 보낸다
        assertThat(script).containsPattern("if \\(session !== dialogSession\\) return null;\\s*return withdraw\\(''\\)\\.then\\(done\\);");
    }

    @Test
    @DisplayName("화면이 쓰는 스타일·스크립트·이미지 파일이 모두 있다")
    void staticFilesExist() {
        for (String path : new String[] {"/static/css/mypage-mg.css", "/static/js/mypage-growth.js",
                "/static/js/mypage-settings.js", "/static/images/cash-coin.png"}) {
            assertThat(MypageFragmentsRenderTest.class.getResource(path)).as(path).isNotNull();
        }
    }
}
