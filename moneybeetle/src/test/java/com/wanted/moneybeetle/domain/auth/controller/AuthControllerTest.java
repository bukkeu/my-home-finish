package com.wanted.moneybeetle.domain.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(accountService, passwordResetService))
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
}
