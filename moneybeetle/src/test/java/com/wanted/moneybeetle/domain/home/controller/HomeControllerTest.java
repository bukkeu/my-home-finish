package com.wanted.moneybeetle.domain.home.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.home.model.dto.HomeDTO;
import com.wanted.moneybeetle.domain.home.model.service.HomeService;
import com.wanted.moneybeetle.domain.member.model.dto.LoginMemberDTO;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.domain.term.model.dto.EconomicTermDTO;
import com.wanted.moneybeetle.global.exception.GlobalExceptionHandler;
import java.time.LocalDate;
import java.util.List;
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

class HomeControllerTest {

    private HomeService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(HomeService.class);
        Member member = Member.signup("minji@gmail.com", "hash", "민지", LocalDate.of(1999, 4, 12), "FEMALE",
                LocalDate.of(2026, 11, 9));
        ReflectionTestUtils.setField(member, "memberId", 8L);
        AuthDetails principal = new AuthDetails(LoginMemberDTO.from(member, Member.ROLE_MEMBER));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        mockMvc = MockMvcBuilders.standaloneSetup(new HomeController(service))
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
    @DisplayName("홈 화면: 명세(API 19)의 필드 이름으로 내보낸다")
    void getsHome() throws Exception {
        when(service.getHome(8L)).thenReturn(new HomeDTO(
                List.of(new EconomicTermDTO(3L, "복리", "이자에 이자가 붙는 방식")),
                new HomeDTO.ContinueCourse(7L, "청약통장 기초와 가점 계산", 21L, "가점은 어떻게 계산할까", 312),
                new HomeDTO.Learning(14, 5),
                new HomeDTO.Training(new HomeDTO.Unlock(true, 8), new HomeDTO.Unlock(false, 12))));

        mockMvc.perform(get("/api/v1/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todayTerms[0].economicTermId").value(3))
                .andExpect(jsonPath("$.data.todayTerms[0].term").value("복리"))
                .andExpect(jsonPath("$.data.continueCourse.courseId").value(7))
                .andExpect(jsonPath("$.data.continueCourse.lessonId").value(21))
                .andExpect(jsonPath("$.data.continueCourse.positionSeconds").value(312))
                .andExpect(jsonPath("$.data.learning.watchedLessons").value(14))
                .andExpect(jsonPath("$.data.learning.completedCourses").value(5))
                .andExpect(jsonPath("$.data.training.trading.unlocked").value(true))
                .andExpect(jsonPath("$.data.training.trading.requiredLevel").value(8))
                .andExpect(jsonPath("$.data.training.housing.unlocked").value(false))
                .andExpect(jsonPath("$.data.training.housing.requiredLevel").value(12));
    }

    @Test
    @DisplayName("이어서 볼 강의가 없으면 continueCourse 는 null 이다")
    void homeWithoutContinueCourse() throws Exception {
        when(service.getHome(8L)).thenReturn(new HomeDTO(List.of(), null, new HomeDTO.Learning(0, 0),
                new HomeDTO.Training(new HomeDTO.Unlock(false, 8), new HomeDTO.Unlock(false, 12))));

        mockMvc.perform(get("/api/v1/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.continueCourse").doesNotExist())
                .andExpect(jsonPath("$.data.todayTerms").isEmpty());
    }
}
