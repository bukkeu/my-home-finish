
package com.wanted.moneybeetle.domain.member.controller;

import com.wanted.moneybeetle.domain.member.model.dto.AvailabilityDTO;
import com.wanted.moneybeetle.domain.member.model.dto.SignupDTO;
import com.wanted.moneybeetle.domain.member.model.dto.SignupResultDTO;
import com.wanted.moneybeetle.domain.member.model.dto.TermsDTO;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.domain.member.model.entity.Terms;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.global.exception.GlobalExceptionHandler;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MemberControllerTest {

    private final MemberService memberService = mock(MemberService.class);

    private MockMvc mockMvc() {
        var json = new MappingJackson2HttpMessageConverter(
                Jackson2ObjectMapperBuilder.json().build());

        return MockMvcBuilders.standaloneSetup(
                        new MemberController(memberService))
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
                .andExpect(jsonPath("$.data[0].title").value("서비스 이용약관"))
                .andExpect(jsonPath("$.data[0].isRequired").value(true));
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
    void 회원가입에_성공하면_201과_회원정보를_반환한다() throws Exception {
        Member member = Member.signup(
                "test@example.com",
                "encoded-password",
                "민지",
                LocalDate.of(2000, 1, 1),
                "FEMALE",
                LocalDate.of(2026, 11, 9)
        );

        when(memberService.signup(any(SignupDTO.class)))
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
