package com.wanted.moneybeetle.domain.operation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.member.model.dto.LoginMemberDTO;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.domain.operation.model.dto.AdminLogDTO;
import com.wanted.moneybeetle.domain.operation.model.dto.AdminLogListDTO;
import com.wanted.moneybeetle.domain.operation.model.dto.SettingDTO;
import com.wanted.moneybeetle.domain.operation.model.dto.SettingListDTO;
import com.wanted.moneybeetle.domain.operation.model.service.AdminLogQueryService;
import com.wanted.moneybeetle.domain.operation.model.service.SystemSettingService;
import com.wanted.moneybeetle.global.exception.GlobalExceptionHandler;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

class AdminOperationControllerTest {

    private SystemSettingService settingService;
    private AdminLogQueryService logQueryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        settingService = mock(SystemSettingService.class);
        logQueryService = mock(AdminLogQueryService.class);
        Member admin = Member.signup("admin@gmail.com", "hash", "관리자", LocalDate.of(1999, 4, 12), null,
                LocalDate.of(2026, 11, 9));
        ReflectionTestUtils.setField(admin, "memberId", 1L);
        AuthDetails principal = new AuthDetails(LoginMemberDTO.from(admin, Member.ROLE_ADMIN));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminOperationController(settingService, logQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("서비스 설정 조회: settings 목록 (API 111)")
    void getsSettings() throws Exception {
        when(settingService.findSettings()).thenReturn(new SettingListDTO(
                List.of(new SettingDTO("UNLOCK_LEVEL_TRADING", "8"), new SettingDTO("QUIZ_PASS_SCORE", "70"))));

        mockMvc.perform(get("/api/v1/admin/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settings[0].key").value("UNLOCK_LEVEL_TRADING"))
                .andExpect(jsonPath("$.data.settings[1].value").value("70"));
    }

    @Test
    @DisplayName("서비스 설정 변경: 로그인한 관리자 번호로 서비스를 부르고 204 (API 112)")
    void updatesSetting() throws Exception {
        mockMvc.perform(put("/api/v1/admin/settings/UNLOCK_LEVEL_TRADING").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"12\"}"))
                .andExpect(status().isNoContent());

        verify(settingService).updateSetting(1L, "UNLOCK_LEVEL_TRADING", "12");
    }

    @Test
    @DisplayName("작업 기록 조회: 조건을 서비스로 넘기고 items 에 detail 을 JSON 객체로 내보낸다 (API 113)")
    void getsAdminLogs() throws Exception {
        var detail = new ObjectMapper().readTree("{\"from\":4,\"to\":5}");
        when(logQueryService.findLogs(any(), any(), any(), any(), any(), any(), any())).thenReturn(new AdminLogListDTO(
                List.of(new AdminLogDTO(77L, "김운영", "TURN_ADVANCE", "SEASON", 7L, detail,
                        LocalDateTime.of(2026, 11, 5, 0, 0, 3)))));

        mockMvc.perform(get("/api/v1/admin/admin-logs")
                        .param("targetType", "SEASON").param("targetId", "7")
                        .param("from", "2026-11-01").param("to", "2026-11-05")
                        .param("page", "0").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].adminNickname").value("김운영"))
                .andExpect(jsonPath("$.data.items[0].detail.to").value(5))
                .andExpect(jsonPath("$.data.items[0].createdAt").value("2026-11-05T00:00:03"));

        verify(logQueryService).findLogs(eq("SEASON"), eq(7L), eq(null), eq(LocalDate.of(2026, 11, 1)),
                eq(LocalDate.of(2026, 11, 5)), eq(0), eq(50));
    }

    @Test
    @DisplayName("서비스 설정 값이 공백이거나 너무 길면 서비스를 부르지 않는다 (응답 코드는 @Valid 예외 처리 #142 가 정한다)")
    void invalidSettingValueSkipsService() throws Exception {
        mockMvc.perform(put("/api/v1/admin/settings/UNLOCK_LEVEL_TRADING").contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":\"   \"}"));
        mockMvc.perform(put("/api/v1/admin/settings/UNLOCK_LEVEL_TRADING").contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":\"" + "9".repeat(201) + "\"}"));

        org.mockito.Mockito.verifyNoInteractions(settingService);
    }
}
