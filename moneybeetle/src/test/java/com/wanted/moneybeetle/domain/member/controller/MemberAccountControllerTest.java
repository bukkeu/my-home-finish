package com.wanted.moneybeetle.domain.member.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.domain.member.model.dto.LoginMemberDTO;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.domain.member.model.service.AccountService;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.GlobalExceptionHandler;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/* comment.
*   담당 : DBA ② (회원·인증)
*   MemberController 비밀번호 변경·회원 탈퇴 (#8, API 16·17) - 서버·DB 없이 MockMvc 로 돈다
*  */
class MemberAccountControllerTest {

    private final AccountService accountService = mock(AccountService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Member member = Member.signup("minji@gmail.com", "hash", "민지", LocalDate.of(1999, 4, 12), "FEMALE",
                LocalDate.of(2026, 11, 9));
        ReflectionTestUtils.setField(member, "memberId", 7L);
        AuthDetails principal = new AuthDetails(LoginMemberDTO.from(member, Member.ROLE_MEMBER));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        mockMvc = MockMvcBuilders.standaloneSetup(new MemberController(mock(MemberService.class), accountService,
                mock(com.wanted.moneybeetle.domain.member.model.service.MemberProfileService.class)))
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
    @DisplayName("비밀번호 변경은 로그인한 회원의 번호로 서비스를 부르고 204")
    void changePassword() throws Exception {
        mockMvc.perform(patch("/api/v1/members/me/password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"beetle1234\",\"newPassword\":\"beetle5678\",\"newPasswordConfirm\":\"beetle5678\"}"))
                .andExpect(status().isNoContent());

        verify(accountService).changePassword(7L, "beetle1234", "beetle5678", "beetle5678");
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 400 PASSWORD_WRONG")
    void wrongCurrentPassword() throws Exception {
        doThrow(new BusinessException(MemberErrorCode.PASSWORD_WRONG))
                .when(accountService).changePassword(7L, "bad", "beetle5678", "beetle5678");

        mockMvc.perform(patch("/api/v1/members/me/password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"bad\",\"newPassword\":\"beetle5678\",\"newPasswordConfirm\":\"beetle5678\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_WRONG"));
    }

    @Test
    @DisplayName("탈퇴하면 204 이고 로그인 세션도 끝난다")
    void withdrawInvalidatesSession() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(delete("/api/v1/members/me").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"beetle1234\"}"))
                .andExpect(status().isNoContent());

        verify(accountService).withdraw(7L, "beetle1234");
        assertTrue(session.isInvalid());
    }

    @Test
    @DisplayName("탈퇴 때 비밀번호가 틀리면 세션을 끝내지 않는다")
    void wrongPasswordKeepsSession() throws Exception {
        doThrow(new BusinessException(MemberErrorCode.PASSWORD_WRONG)).when(accountService).withdraw(7L, "bad");
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(delete("/api/v1/members/me").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"bad\"}"))
                .andExpect(status().isBadRequest());

        assertTrue(!session.isInvalid());
    }
}
