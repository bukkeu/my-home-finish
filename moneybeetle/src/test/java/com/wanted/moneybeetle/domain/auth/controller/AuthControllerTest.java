package com.wanted.moneybeetle.domain.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wanted.moneybeetle.domain.auth.model.service.IdentityVerificationService;
import com.wanted.moneybeetle.domain.auth.model.service.PasswordResetService;
import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.domain.member.model.dto.MaskedEmailDTO;
import com.wanted.moneybeetle.domain.member.model.service.AccountService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.GlobalExceptionHandler;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/* comment.
*   담당 : DBA ② (회원·인증)
*   AuthController 아이디 찾기·비밀번호 재설정 (#8, API 9~11) - 서버·DB 없이 MockMvc 로 돈다
*  */
class AuthControllerTest {

    private final AccountService accountService = mock(AccountService.class);
    private final PasswordResetService passwordResetService = mock(PasswordResetService.class);
    private final IdentityVerificationService identityVerificationService = mock(IdentityVerificationService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(accountService, passwordResetService, identityVerificationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json().build()))
                .build();
    }

    @Test
    @DisplayName("아이디 찾기는 가린 이메일을 돌려준다")
    void findEmail() throws Exception {
        when(accountService.findMaskedEmail("민지", LocalDate.of(1999, 4, 12))).thenReturn(new MaskedEmailDTO("mi***@gmail.com"));

        mockMvc.perform(post("/api/v1/auth/find-email").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"민지\",\"birthDate\":\"1999-04-12\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maskedEmail").value("mi***@gmail.com"));
    }

    @Test
    @DisplayName("맞는 회원이 없으면 404 MEMBER_NOT_FOUND")
    void findEmailNotFound() throws Exception {
        when(accountService.findMaskedEmail(any(), any())).thenThrow(new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));

        mockMvc.perform(post("/api/v1/auth/find-email").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"없음\",\"birthDate\":\"1999-04-12\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    @DisplayName("재설정 요청은 가입 여부와 상관없이 202")
    void requestReset() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"minji@gmail.com\"}"))
                .andExpect(status().isAccepted());

        verify(passwordResetService).requestReset("minji@gmail.com");
    }

    @Test
    @DisplayName("재설정 확인은 204, 토큰이 만료되면 400 TOKEN_EXPIRED")
    void confirmReset() throws Exception {
        String body = "{\"token\":\"t\",\"newPassword\":\"beetle5678\",\"newPasswordConfirm\":\"beetle5678\"}";

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());

        Mockito.doThrow(new BusinessException(MemberErrorCode.TOKEN_EXPIRED))
                .when(passwordResetService).confirmReset("t", "beetle5678", "beetle5678");
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("TOKEN_EXPIRED"));
    }

    @Test
    @DisplayName("형식이 틀린 요청은 서비스를 부르지 않는다")
    void invalidRequestSkipsService() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\"}"));

        verify(passwordResetService, never()).requestReset(any());
    }

    @Test
    @DisplayName("본인인증 코드 요청은 202 이고 서비스에 정규화 전 이메일을 그대로 넘긴다")
    void requestIdentityCode() throws Exception {
        mockMvc.perform(post("/api/v1/auth/identity-verification/request").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"minji@gmail.com\"}"))
                .andExpect(status().isAccepted());

        verify(identityVerificationService).requestCode(Mockito.eq("minji@gmail.com"), Mockito.eq("127.0.0.1"), any());
    }

    @Test
    @DisplayName("코드를 너무 자주 요청하면 429 IDENTITY_CODE_TOO_SOON")
    void requestIdentityCodeTooSoon() throws Exception {
        Mockito.doThrow(new BusinessException(MemberErrorCode.IDENTITY_CODE_TOO_SOON))
                .when(identityVerificationService).requestCode(any(), any(), any());

        mockMvc.perform(post("/api/v1/auth/identity-verification/request").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"minji@gmail.com\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("IDENTITY_CODE_TOO_SOON"));
    }

    @Test
    @DisplayName("접속 주소별 요청 한도를 넘으면 429 IDENTITY_REQUEST_LIMITED")
    void requestIdentityCodeIpLimited() throws Exception {
        Mockito.doThrow(new BusinessException(MemberErrorCode.IDENTITY_REQUEST_LIMITED))
                .when(identityVerificationService).requestCode(any(), any(), any());

        mockMvc.perform(post("/api/v1/auth/identity-verification/request").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"minji@gmail.com\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("IDENTITY_REQUEST_LIMITED"));
    }

    @Test
    @DisplayName("이메일 형식이 틀린 코드 요청은 서비스를 부르지 않고 400")
    void requestIdentityCodeInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/identity-verification/request").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());

        verify(identityVerificationService, never()).requestCode(any(), any(), any());
    }

    @Test
    @DisplayName("맞는 코드로 본인인증하면 verified: true")
    void verifyIdentity() throws Exception {
        mockMvc.perform(post("/api/v1/auth/identity-verification").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"minji@gmail.com\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verified").value(true));

        verify(identityVerificationService).verify(Mockito.eq("minji@gmail.com"), Mockito.eq("123456"), any());
    }

    @Test
    @DisplayName("틀린 코드는 400 IDENTITY_CODE_INVALID")
    void verifyIdentityWrongCode() throws Exception {
        Mockito.doThrow(new BusinessException(MemberErrorCode.IDENTITY_CODE_INVALID))
                .when(identityVerificationService).verify(any(), any(), any());

        mockMvc.perform(post("/api/v1/auth/identity-verification").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"minji@gmail.com\",\"code\":\"000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("IDENTITY_CODE_INVALID"));
    }

    @Test
    @DisplayName("코드가 숫자 6자리가 아니면 서비스를 부르지 않고 400")
    void verifyIdentityInvalidCodeFormat() throws Exception {
        mockMvc.perform(post("/api/v1/auth/identity-verification").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"minji@gmail.com\",\"code\":\"12ab\"}"))
                .andExpect(status().isBadRequest());

        verify(identityVerificationService, never()).verify(any(), any(), any());
    }
}
