package com.wanted.moneybeetle.domain.home.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.home.model.dto.SearchResultDTO;
import com.wanted.moneybeetle.domain.home.model.service.HomeService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/* comment.
*   담당 : PM (#15)
*   HomeController 통합 검색 확인 (서버·DB 없이 Service 는 가짜(mock))
*   - 로그인 회원 번호를 세션(@AuthenticationPrincipal)에서 꺼내 Service 에 넘긴다 -> 종목 검색(Lv.8 해금)에 쓰인다
*   - 로그인 정보가 없으면 null 을 넘긴다
*  */
class HomeControllerTest {

    private HomeService homeService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        homeService = mock(HomeService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new HomeController(homeService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(Long memberId) {
        AuthDetails me = mock(AuthDetails.class);
        when(me.getMemberId()).thenReturn(memberId);
        SecurityContextHolder.setContext(new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken(me, null, List.of())));
    }

    @Test
    void 로그인한_회원의_번호를_검색에_넘긴다() throws Exception {
        loginAs(8L);
        when(homeService.search("청약", 8L)).thenReturn(SearchResultDTO.empty());

        mockMvc.perform(get("/api/v1/search").param("keyword", "청약"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.courses").isEmpty());

        verify(homeService).search("청약", 8L);
    }

    @Test
    void 로그인_정보가_없으면_회원_번호는_null() throws Exception {
        when(homeService.search("청약", null)).thenReturn(SearchResultDTO.empty());

        mockMvc.perform(get("/api/v1/search").param("keyword", "청약")).andExpect(status().isOk());

        verify(homeService).search("청약", null);
    }
}
