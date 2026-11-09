package com.wanted.moneybeetle.domain.growth.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.growth.model.dto.AttendanceStatusDTO;
import com.wanted.moneybeetle.domain.growth.model.service.AttendanceService;
import com.wanted.moneybeetle.domain.member.model.dto.LoginMemberDTO;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
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

/* comment.
*   담당 : DBA ② (성장·캐시·레벨)
*   GrowthController 출석 현황 (이슈 #12, API 21) - 서버·DB 없이 MockMvc 로 돈다
*  */
class GrowthControllerTest {

    private final AttendanceService attendanceService = mock(AttendanceService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Member member = Member.signup("minji@gmail.com", "hash", "민지", LocalDate.of(1999, 4, 12), "FEMALE",
                LocalDate.of(2026, 11, 9));
        ReflectionTestUtils.setField(member, "memberId", 7L);
        AuthDetails principal = new AuthDetails(LoginMemberDTO.from(member, Member.ROLE_MEMBER));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        mockMvc = MockMvcBuilders.standaloneSetup(new GrowthController(attendanceService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json().build()))
                .build();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("출석 현황은 로그인한 회원의 번호로 조회하고 명세서 형식으로 돌려준다")
    void getAttendance() throws Exception {
        when(attendanceService.getStatus(7L)).thenReturn(new AttendanceStatusDTO(3, 2, 10_000, true));

        mockMvc.perform(get("/api/v1/members/me/attendance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.streak").value(3))
                .andExpect(jsonPath("$.data.daysToReward").value(2))
                .andExpect(jsonPath("$.data.rewardCash").value(10_000))
                .andExpect(jsonPath("$.data.checkedToday").value(true));

        verify(attendanceService).getStatus(7L);
    }
}
