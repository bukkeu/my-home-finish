
package com.wanted.moneybeetle.domain.member.controller;

import com.wanted.moneybeetle.domain.member.model.dto.AvailabilityDTO;
import com.wanted.moneybeetle.domain.member.model.dto.SignupDTO;
import com.wanted.moneybeetle.domain.member.model.dto.SignupResultDTO;
import com.wanted.moneybeetle.domain.member.model.dto.TermsDTO;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.domain.member.model.entity.Terms;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MemberControllerTest {

    private final MemberService memberService = mock(MemberService.class);
    private final com.wanted.moneybeetle.domain.member.model.service.MemberSignupService memberSignupService = mock(com.wanted.moneybeetle.domain.member.model.service.MemberSignupService.class);

    private MockMvc mockMvc() {
        var json = new MappingJackson2HttpMessageConverter(
                Jackson2ObjectMapperBuilder.json().build());

        return MockMvcBuilders.standaloneSetup(
                        new MemberController(memberService,
                                mock(com.wanted.moneybeetle.domain.member.model.service.AccountService.class),
                                mock(com.wanted.moneybeetle.domain.member.model.service.MemberProfileService.class),
                                memberSignupService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(json)
                .build();
    }

    @Test
    void 약관_목록을_조회한다() throws Exception {
        Terms terms = Terms.create(
                "서비스 이용약관",
                "서비스 이용에 관한 약관입니다.",
                true,
                "1.0",
                LocalDateTime.of(2026, 1, 1, 0, 0)
        );

        when(memberService.findAgreements())
                .thenReturn(List.of(TermsDTO.from(terms)));

        mockMvc().perform(get("/api/v1/agreements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.agreements[0].title").value("서비스 이용약관"))
                .andExpect(jsonPath("$.data.agreements[0].isRequired").value(true));
    }

    @Test
    void 이메일_중복을_확인한다() throws Exception {
        when(memberService.isEmailAvailable("test@example.com"))
                .thenReturn(AvailabilityDTO.of(true));

        mockMvc().perform(get("/api/v1/auth/check-email")
                        .param("email", "test@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.available").value(true));
    }

    @Test
    void 닉네임_중복을_확인한다() throws Exception {
        when(memberService.isNicknameAvailable("민지"))
                .thenReturn(AvailabilityDTO.of(true));

        mockMvc().perform(get("/api/v1/auth/check-nickname")
                        .param("nickname", "민지"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.available").value(true));
    }

    @Test
    void 닉네임_형식이_틀리면_400과_오류_코드를_반환한다() throws Exception {
        when(memberService.isNicknameAvailable("!"))
                .thenThrow(new BusinessException(MemberErrorCode.NICKNAME_INVALID));

        mockMvc().perform(get("/api/v1/auth/check-nickname").param("nickname", "!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NICKNAME_INVALID"));
    }

    @Test
    void 이미_가입된_이메일이면_가입_요청은_409와_오류_코드를_반환한다() throws Exception {
        when(memberSignupService.signup(any(SignupDTO.class)))
                .thenThrow(new BusinessException(MemberErrorCode.EMAIL_DUPLICATED));

        mockMvc().perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSignupJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("EMAIL_DUPLICATED"));
    }

    /**
     * 지금은 500 이다: GlobalExceptionHandler(global, 서수영 담당)에 @Valid 실패(MethodArgumentNotValidException) 처리가 없어
     * 일반 예외로 잡혀 INTERNAL_ERROR 가 나간다. API 명세서는 입력 오류를 400 + error.fields 로 정했다.
     * 담당자가 그 핸들러를 추가하면 @Disabled 를 지워 켠다.
     */
    @Test
    @Disabled("GlobalExceptionHandler 에 @Valid 실패 → 400 처리가 생기면 켠다 (global 담당에게 요청)")
    void 잘못된_가입_요청_본문은_서비스를_부르지_않고_400을_반환한다() throws Exception {
        // 비밀번호 형식 오류 + 약관 ID 누락
        String invalid = """
                {
                    "email": "test@example.com",
                    "password": "short",
                    "passwordConfirm": "short",
                    "nickname": "민지",
                    "birthDate": "2000-01-01",
                    "gender": "FEMALE"
                }
                """;

        mockMvc().perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        verify(memberSignupService, never()).signup(any(SignupDTO.class));
    }

    private static String validSignupJson() {
        return """
                {
                    "email": "test@example.com",
                    "password": "test1234",
                    "passwordConfirm": "test1234",
                    "nickname": "민지",
                    "birthDate": "2000-01-01",
                    "gender": "FEMALE",
                    "agreedTermsIds": [1]
                }
                """;
    }

    @Test
    void 회원가입에_성공하면_201과_회원정보를_반환한다() throws Exception {
        Member member = Member.signup(
                "test@example.com",
                "encoded-password",
                "민지",
                LocalDate.of(2000, 1, 1),
                "FEMALE",
                LocalDate.of(2026, 11, 9)
        );

        when(memberSignupService.signup(any(SignupDTO.class)))
                .thenReturn(SignupResultDTO.from(member));

        String request = """
                {
                    "email": "test@example.com",
                    "password": "test1234",
                    "passwordConfirm": "test1234",
                    "nickname": "민지",
                    "birthDate": "2000-01-01",
                    "gender": "FEMALE",
                    "agreedTermsIds": [1]
                }
                """;

        mockMvc().perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nickname").value("민지"));
    }
}
