package com.wanted.moneybeetle.domain.home.config;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.home.model.dto.LayoutDTO;
import com.wanted.moneybeetle.domain.home.model.service.LayoutService;
import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.global.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/* comment.
*   담당 : DBA ② (홈, 이슈 #248)
*   LayoutInterceptor - 학습자 화면에만, 컨트롤러가 정하지 않았을 때만 layout 을 넣고, 값을 못 만들어도 화면은 그대로 나가는지 서버 없이 확인한다.
*  */
class LayoutInterceptorTest {

    @Controller
    static class TestPages {
        @GetMapping("/user/page")
        String page() {
            return "pages/page";
        }

        @GetMapping("/user/own-layout")
        String own(Model model) {
            model.addAttribute("layout", "컨트롤러가 정한 값");
            return "pages/own";
        }

        @GetMapping("/user/go")
        String go() {
            return "redirect:/user/home";
        }

        @GetMapping("/user/api")
        @ResponseBody
        String api() {
            return "{\"ok\":true}";
        }
    }

    private final LayoutService layoutService = mock(LayoutService.class);
    private final LayoutDTO layout = new LayoutDTO("민지", 4, "원룸", 12400L, 60, 150, 2000L, 4, "", 8, 12);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(layoutService.getLayout(7L)).thenReturn(layout);
        mockMvc = MockMvcBuilders.standaloneSetup(new TestPages()).addInterceptors(new LayoutInterceptor(layoutService)).build();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String... authorities) {
        AuthDetails details = mock(AuthDetails.class);
        when(details.getMemberId()).thenReturn(7L);
        List<GrantedAuthority> granted = java.util.Arrays.stream(authorities).<GrantedAuthority>map(SimpleGrantedAuthority::new).toList();
        doReturn(granted).when(details).getAuthorities();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(details, null, granted));
    }

    @Test
    @DisplayName("로그인한 학습자의 화면에는 Model 에 layout 이 들어간다")
    void addsLayoutForMember() throws Exception {
        loginAs("MEMBER");

        mockMvc.perform(get("/user/page")).andExpect(status().isOk()).andExpect(model().attribute("layout", layout));
    }

    @Test
    @DisplayName("컨트롤러가 이미 layout 을 정했으면 덮어쓰지 않고 서비스도 부르지 않는다")
    void keepsControllerLayout() throws Exception {
        loginAs("MEMBER");

        mockMvc.perform(get("/user/own-layout")).andExpect(model().attribute("layout", "컨트롤러가 정한 값"));

        verify(layoutService, never()).getLayout(anyLong());
    }

    @Test
    @DisplayName("로그인하지 않았거나 학습자가 아니면(강사·관리자) layout 을 넣지 않는다")
    void notForOthers() throws Exception {
        mockMvc.perform(get("/user/page")).andExpect(model().attributeDoesNotExist("layout"));

        loginAs("INSTRUCTOR");
        mockMvc.perform(get("/user/page")).andExpect(model().attributeDoesNotExist("layout"));

        loginAs("ADMIN");
        mockMvc.perform(get("/user/page")).andExpect(model().attributeDoesNotExist("layout"));

        verifyNoInteractions(layoutService);
    }

    @Test
    @DisplayName("리다이렉트 응답과 REST API 응답에는 서비스를 부르지 않는다")
    void notForRedirectOrApi() throws Exception {
        loginAs("MEMBER");

        mockMvc.perform(get("/user/go")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/user/api")).andExpect(status().isOk());

        verifyNoInteractions(layoutService);
    }

    @Test
    @DisplayName("값을 만들다 실패해도(탈퇴·정지 계정, 예기치 않은 오류) 화면은 그대로 나가고 layout 만 빠진다")
    void failureKeepsPage() throws Exception {
        loginAs("MEMBER");
        doThrow(new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND)).when(layoutService).getLayout(7L);

        mockMvc.perform(get("/user/page")).andExpect(status().isOk()).andExpect(model().attributeDoesNotExist("layout"));

        doThrow(new IllegalStateException("레벨표가 비었어요")).when(layoutService).getLayout(7L);
        mockMvc.perform(get("/user/page")).andExpect(status().isOk()).andExpect(model().attributeDoesNotExist("layout"));
    }
}
