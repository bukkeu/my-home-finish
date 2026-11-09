package com.wanted.moneybeetle.domain.growth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.growth.model.dto.RewardRuleDTO;
import com.wanted.moneybeetle.domain.growth.model.dto.RewardRuleListDTO;
import com.wanted.moneybeetle.domain.growth.model.dto.RewardRuleUpdateDTO;
import com.wanted.moneybeetle.domain.growth.model.service.RewardRuleService;
import com.wanted.moneybeetle.domain.member.model.dto.LoginMemberDTO;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.global.exception.GlobalExceptionHandler;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminGrowthControllerTest {

    private RewardRuleService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(RewardRuleService.class);
        Member admin = Member.signup("admin@gmail.com", "hash", "관리자", LocalDate.of(1999, 4, 12), null,
                LocalDate.of(2026, 11, 9));
        ReflectionTestUtils.setField(admin, "memberId", 1L);
        AuthDetails principal = new AuthDetails(LoginMemberDTO.from(admin, Member.ROLE_ADMIN));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminGrowthController(service, mock(com.wanted.moneybeetle.domain.growth.model.service.CashAdjustService.class)))
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
    @DisplayName("보상 규칙 조회: rules 목록을 isActive 필드명으로 돌려준다 (API 108)")
    void getsRules() throws Exception {
        when(service.findRules()).thenReturn(new RewardRuleListDTO(
                List.of(new RewardRuleDTO(1L, "COURSE_COMPLETE", "학습 완료", 3000, 100, true))));

        mockMvc.perform(get("/api/v1/admin/reward-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rules[0].code").value("COURSE_COMPLETE"))
                .andExpect(jsonPath("$.data.rules[0].cashAmount").value(3000))
                .andExpect(jsonPath("$.data.rules[0].isActive").value(true));
    }

    @Test
    @DisplayName("보상 규칙 수정: 로그인한 관리자 번호로 서비스를 부르고 204 (API 109)")
    void updatesRule() throws Exception {
        mockMvc.perform(put("/api/v1/admin/reward-rules/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cashAmount\":5000,\"expAmount\":200,\"isActive\":true}"))
                .andExpect(status().isNoContent());

        verify(service).updateRule(eq(1L), eq(1L), any(RewardRuleUpdateDTO.class));
    }
}
