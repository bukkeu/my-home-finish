package com.wanted.moneybeetle.domain.member.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.domain.member.model.dto.InstructorProfileDTO;
import com.wanted.moneybeetle.domain.member.model.dto.InstructorProfileUpdateDTO;
import com.wanted.moneybeetle.domain.member.model.dto.LoginMemberDTO;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.domain.member.model.service.InstructorProfileService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
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

/* comment.
*   담당 : DBA ② (회원·인증)
*   InstructorProfileController (이슈 #365, API 12-1·12-2) - 서버·DB 없이 MockMvc 로 돈다
*   - 학생(MEMBER) 계정이 403 인 것은 SecurityConfigTest 가 확인한다 (URL 권한은 SecurityConfig 몫)
*  */
class InstructorProfileControllerTest {

    private static final String URL = "/api/v1/instructor/profile";
    private static final String VALID = """
            { "displayName": "박서연", "headline": "청약 가점, 한 번에", "bio": "10년차 공인중개사입니다.", "fields": ["청약", "부동산"] }
            """;

    private final InstructorProfileService service = mock(InstructorProfileService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Member member = Member.signup("teacher@moneybeetle.kr", "hash", "서연", LocalDate.of(1990, 1, 2), "FEMALE",
                LocalDate.of(2026, 11, 8));
        ReflectionTestUtils.setField(member, "memberId", 7L);
        AuthDetails principal = new AuthDetails(LoginMemberDTO.from(member, "INSTRUCTOR"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        mockMvc = MockMvcBuilders.standaloneSetup(new InstructorProfileController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json().build()))
                .build();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private String body(String displayName, String headline, String bio, String fields) {
        return "{\"displayName\":" + displayName + ",\"headline\":" + headline + ",\"bio\":" + bio + ",\"fields\":" + fields + "}";
    }

    @Test
    @DisplayName("조회: 로그인한 강사의 번호로 서비스를 불러 { displayName, headline, bio, fields } 를 돌려준다")
    void getProfile() throws Exception {
        when(service.getMyProfile(7L)).thenReturn(
                new InstructorProfileDTO("박서연", "청약 가점, 한 번에", "10년차 공인중개사입니다.", List.of("청약", "부동산")));

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.displayName").value("박서연"))
                .andExpect(jsonPath("$.data.headline").value("청약 가점, 한 번에"))
                .andExpect(jsonPath("$.data.bio").value("10년차 공인중개사입니다."))
                .andExpect(jsonPath("$.data.fields[0]").value("청약"))
                .andExpect(jsonPath("$.data.fields[1]").value("부동산"));
    }

    @Test
    @DisplayName("조회: 강사 정보가 없으면 서비스의 오류 코드(404)를 그대로 돌려준다")
    void getProfileNotFound() throws Exception {
        when(service.getMyProfile(7L)).thenThrow(new BusinessException(MemberErrorCode.INSTRUCTOR_PROFILE_NOT_FOUND));

        mockMvc.perform(get(URL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("INSTRUCTOR_PROFILE_NOT_FOUND"));
    }

    @Test
    @DisplayName("수정: 요청 값을 로그인한 강사의 번호와 함께 서비스에 넘기고 저장된 값을 돌려준다")
    void patchProfile() throws Exception {
        when(service.updateMyProfile(eq(7L), any(InstructorProfileUpdateDTO.class))).thenReturn(
                new InstructorProfileDTO("박서연", "청약 가점, 한 번에", "10년차 공인중개사입니다.", List.of("청약", "부동산")));

        mockMvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("박서연"))
                .andExpect(jsonPath("$.data.fields[1]").value("부동산"));
        verify(service).updateMyProfile(eq(7L), any(InstructorProfileUpdateDTO.class));
    }

    @Test
    @DisplayName("수정: 한 줄 소개·강사 소개는 없어도(null) 된다")
    void patchWithoutHeadlineAndBio() throws Exception {
        when(service.updateMyProfile(eq(7L), any(InstructorProfileUpdateDTO.class)))
                .thenReturn(new InstructorProfileDTO("박서연", null, null, List.of("대출")));

        mockMvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"박서연\",\"fields\":[\"대출\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.headline").doesNotExist());
    }

    @Test
    @DisplayName("수정: 이름이 비었거나 2자 미만·20자 초과·공백뿐이면 INVALID_INPUT 이고 서비스를 부르지 않는다")
    void patchInvalidDisplayName() throws Exception {
        for (String name : new String[] {"null", "\"\"", "\"   \"", "\"박\"", "\"  박  \"", "\"" + "가".repeat(21) + "\""}) {
            mockMvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content(body(name, "null", "null", "[\"청약\"]")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        }
        verify(service, never()).updateMyProfile(any(), any());
    }

    @Test
    @DisplayName("수정: 한 줄 소개 60자·강사 소개 1,000자는 되고 1자 넘으면 INVALID_INPUT")
    void patchHeadlineAndBioLimits() throws Exception {
        when(service.updateMyProfile(eq(7L), any(InstructorProfileUpdateDTO.class)))
                .thenReturn(new InstructorProfileDTO("박서연", "가".repeat(60), "가".repeat(1000), List.of("청약")));

        mockMvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"박서연\"", "\"" + "가".repeat(60) + "\"", "\"" + "가".repeat(1000) + "\"", "[\"청약\"]")))
                .andExpect(status().isOk());

        mockMvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"박서연\"", "\"" + "가".repeat(61) + "\"", "null", "[\"청약\"]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        mockMvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"박서연\"", "null", "\"" + "가".repeat(1001) + "\"", "[\"청약\"]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("수정: 전문 분야가 없거나 비었거나 고를 수 없는 값이 있으면 INVALID_INPUT 이고 서비스를 부르지 않는다")
    void patchInvalidFields() throws Exception {
        for (String fields : new String[] {"null", "[]", "[\"암호화폐\"]", "[\"청약\",\"암호화폐\"]", "[\"\"]", "[null]", "[\"etf\"]"}) {
            mockMvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content(body("\"박서연\"", "null", "null", fields)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        }
        verify(service, never()).updateMyProfile(any(), any());
    }

    @Test
    @DisplayName("수정: 고를 수 있는 분야 7개를 모두 보내면 통과한다")
    void patchAllFields() throws Exception {
        when(service.updateMyProfile(eq(7L), any(InstructorProfileUpdateDTO.class)))
                .thenReturn(new InstructorProfileDTO("박서연", null, null, List.of("청약")));

        mockMvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"박서연\"", "null", "null", "[\"청약\",\"ETF\",\"주식\",\"부동산\",\"예적금\",\"재테크 기초\",\"대출\"]")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("수정: 금칙어 오류는 서비스의 오류 코드(400)를 그대로 돌려준다")
    void patchForbiddenWord() throws Exception {
        when(service.updateMyProfile(eq(7L), any(InstructorProfileUpdateDTO.class)))
                .thenThrow(new BusinessException(CommonErrorCode.FORBIDDEN_WORD));

        mockMvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN_WORD"));
    }
}
