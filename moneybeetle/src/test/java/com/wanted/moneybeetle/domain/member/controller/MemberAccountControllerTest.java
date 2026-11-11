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
    private final com.wanted.moneybeetle.domain.member.model.service.WithdrawVerifier withdrawVerifier =
            mock(com.wanted.moneybeetle.domain.member.model.service.WithdrawVerifier.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Member member = Member.signup("minji@gmail.com", "hash", "민지", LocalDate.of(1999, 4, 12), "FEMALE",
                LocalDate.of(2026, 11, 9));
        ReflectionTestUtils.setField(member, "memberId", 7L);
        AuthDetails principal = new AuthDetails(LoginMemberDTO.from(member, Member.ROLE_MEMBER));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        mockMvc = MockMvcBuilders.standaloneSetup(new MemberController(mock(MemberService.class), accountService, withdrawVerifier,
                mock(com.wanted.moneybeetle.domain.member.model.service.MemberProfileService.class),
                mock(com.wanted.moneybeetle.domain.member.model.service.MemberSignupService.class)))
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

        verify(accountService).withdraw(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq("beetle1234"), org.mockito.ArgumentMatchers.any());
        assertTrue(session.isInvalid());
    }

    @Test
    @DisplayName("간편 로그인 회원은 본문 없이 탈퇴를 요청한다 (비밀번호 없이 서비스가 본인 확인 여부를 본다)")
    void withdrawWithoutBody() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(delete("/api/v1/members/me").session(session))
                .andExpect(status().isNoContent());

        verify(accountService).withdraw(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any());
        assertTrue(session.isInvalid());
    }

    @Test
    @DisplayName("간편 로그인 회원이 이메일 코드로 본인 확인을 하지 않았으면 WITHDRAW_VERIFICATION_REQUIRED 이고 세션을 끝내지 않는다")
    void withdrawRequiresVerification() throws Exception {
        doThrow(new BusinessException(MemberErrorCode.WITHDRAW_VERIFICATION_REQUIRED)).when(accountService)
                .withdraw(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any());
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(delete("/api/v1/members/me").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("WITHDRAW_VERIFICATION_REQUIRED"));

        assertTrue(!session.isInvalid());
    }

    @Test
    @DisplayName("탈퇴 본인 확인 코드 요청(API 17-1)은 로그인한 회원의 번호로 서비스를 부르고 202 를 돌려준다")
    void requestWithdrawCode() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/members/me/withdraw-verification/request").session(session))
                .andExpect(status().isAccepted());

        verify(withdrawVerifier).requestCode(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("탈퇴 본인 확인 코드 요청은 서비스의 오류(예: 60초 제한)를 그대로 돌려준다")
    void requestWithdrawCodeTooSoon() throws Exception {
        doThrow(new BusinessException(MemberErrorCode.IDENTITY_CODE_TOO_SOON)).when(withdrawVerifier)
                .requestCode(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/members/me/withdraw-verification/request"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("IDENTITY_CODE_TOO_SOON"));
    }

    @Test
    @DisplayName("탈퇴 본인 확인(API 17-2)은 6자리 코드를 서비스에 넘기고 204 를 돌려준다")
    void verifyWithdrawCode() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/members/me/withdraw-verification")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().isNoContent());

        verify(withdrawVerifier).verify(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq("123456"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("탈퇴 본인 확인 코드가 6자리 숫자가 아니면 400 이고 서비스를 부르지 않는다")
    void verifyWithdrawCodeValidation() throws Exception {
        for (String body : new String[] {"{\"code\":\"12345\"}", "{\"code\":\"abcdef\"}", "{\"code\":\"\"}", "{}"}) {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/members/me/withdraw-verification")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }

        org.mockito.Mockito.verifyNoInteractions(withdrawVerifier);
    }

    @Test
    @DisplayName("탈퇴 때 비밀번호가 틀리면 세션을 끝내지 않는다")
    void wrongPasswordKeepsSession() throws Exception {
        doThrow(new BusinessException(MemberErrorCode.PASSWORD_WRONG)).when(accountService)
                .withdraw(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq("bad"), org.mockito.ArgumentMatchers.any());
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(delete("/api/v1/members/me").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"bad\"}"))
                .andExpect(status().isBadRequest());

        assertTrue(!session.isInvalid());
    }
}
