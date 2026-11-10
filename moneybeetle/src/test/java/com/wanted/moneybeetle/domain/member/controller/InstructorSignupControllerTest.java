package com.wanted.moneybeetle.domain.member.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.domain.member.model.dto.InstructorSignupDTO;
import com.wanted.moneybeetle.domain.member.model.dto.InstructorSignupResultDTO;
import com.wanted.moneybeetle.domain.member.model.service.AccountService;
import com.wanted.moneybeetle.domain.member.model.service.MemberProfileService;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.member.model.service.MemberSignupService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/* comment.
*   담당 : DBA ① 신아림 (#30, API 12 강사 가입 신청)
*   POST /api/v1/auth/instructor-signup : 201 { memberId, approvalStatus }, 입력 오류 400, 서비스 오류 코드 그대로
*  */
class InstructorSignupControllerTest {

    private final MemberSignupService memberSignupService = mock(MemberSignupService.class);

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(new MemberController(mock(MemberService.class), mock(AccountService.class),
                        mock(MemberProfileService.class), memberSignupService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json().build()))
                .build();
    }

    private static final String VALID = """
            { "email": "teacher@moneybeetle.kr", "password": "teach1234", "passwordConfirm": "teach1234",
              "displayName": "박서연", "field": "청약, ETF", "agreedTermsIds": [1, 2] }
            """;

    @Test
    @DisplayName("신청하면 201 과 회원 번호·승인 대기 상태를 돌려준다")
    void created() throws Exception {
        when(memberSignupService.signupInstructor(any(InstructorSignupDTO.class), any()))
                .thenReturn(InstructorSignupResultDTO.of(31L, "PENDING"));

        mockMvc().perform(post("/api/v1/auth/instructor-signup").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.memberId").value(31))
                .andExpect(jsonPath("$.data.approvalStatus").value("PENDING"));
    }

    @Test
    @DisplayName("강사명 형식이 틀리거나 비밀번호가 다르면 서비스를 부르지 않고 400")
    void invalidBody() throws Exception {
        String invalid = """
                { "email": "teacher@moneybeetle.kr", "password": "teach1234", "passwordConfirm": "teach9999",
                  "displayName": "!", "field": "청약", "agreedTermsIds": [1] }
                """;

        mockMvc().perform(post("/api/v1/auth/instructor-signup").contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        verify(memberSignupService, never()).signupInstructor(any(), any());
    }

    @Test
    @DisplayName("본인인증을 하지 않았으면 서비스 오류 코드(IDENTITY_NOT_VERIFIED)를 그대로 돌려준다")
    void notVerified() throws Exception {
        when(memberSignupService.signupInstructor(any(InstructorSignupDTO.class), any()))
                .thenThrow(new BusinessException(MemberErrorCode.IDENTITY_NOT_VERIFIED));

        mockMvc().perform(post("/api/v1/auth/instructor-signup").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("IDENTITY_NOT_VERIFIED"));
    }
}
