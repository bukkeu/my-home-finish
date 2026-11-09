package com.wanted.moneybeetle.domain.operation.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.member.model.dto.LoginMemberDTO;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.domain.operation.model.dto.DashboardDTO;
import com.wanted.moneybeetle.domain.operation.model.service.DashboardService;
import com.wanted.moneybeetle.global.exception.GlobalExceptionHandler;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminDashboardControllerTest {

    private DashboardService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(DashboardService.class);
        Member admin = Member.signup("admin@gmail.com", "hash", "관리자", LocalDate.of(1999, 4, 12), null,
                LocalDate.of(2026, 11, 9));
        ReflectionTestUtils.setField(admin, "memberId", 1L);
        AuthDetails principal = new AuthDetails(LoginMemberDTO.from(admin, Member.ROLE_ADMIN));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminDashboardController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json().build()))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("대시보드: 명세(API 84)의 필드 이름으로 내보낸다")
    void getsDashboard() throws Exception {
        when(service.getDashboard(1L)).thenReturn(new DashboardDTO(3, 2,
                new DashboardDTO.Season(7L, 5, 1240, 3820L, false, new DashboardDTO.Missing(2, 0)),
                new DashboardDTO.Members(5, 1)));

        mockMvc.perform(get("/api/v1/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewWaiting").value(3))
                .andExpect(jsonPath("$.data.inquiryWaiting").value(2))
                .andExpect(jsonPath("$.data.season.seasonId").value(7))
                .andExpect(jsonPath("$.data.season.currentTurn").value(5))
                .andExpect(jsonPath("$.data.season.participants").value(1240))
                .andExpect(jsonPath("$.data.season.todayOrders").value(3820))
                .andExpect(jsonPath("$.data.season.nextTurnReady").value(false))
                .andExpect(jsonPath("$.data.season.missing.prices").value(2))
                .andExpect(jsonPath("$.data.members.total").value(5))
                .andExpect(jsonPath("$.data.members.withdrawn").value(1));
    }

    @Test
    @DisplayName("진행 중인 시즌이 없으면 season 은 null 이다")
    void dashboardWithoutSeason() throws Exception {
        when(service.getDashboard(1L)).thenReturn(new DashboardDTO(0, 0, null, new DashboardDTO.Members(0, 0)));

        mockMvc.perform(get("/api/v1/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.season").doesNotExist())
                .andExpect(jsonPath("$.data.reviewWaiting").value(0));
    }
}
