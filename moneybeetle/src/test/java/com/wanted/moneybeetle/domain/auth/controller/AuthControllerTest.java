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
    private final com.wanted.moneybeetle.domain.auth.model.service.SocialLoginService socialLoginService =
            mock(com.wanted.moneybeetle.domain.auth.model.service.SocialLoginService.class);
    private final com.wanted.moneybeetle.domain.auth.handler.AuthSessionWriter authSessionWriter =
            mock(com.wanted.moneybeetle.domain.auth.handler.AuthSessionWriter.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(accountService, passwordResetService, identityVerificationService,
                socialLoginService, authSessionWriter))
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

    // ---- 간편 로그인 (#9, API 8·8-1·8-2) ----

    @Test
    @DisplayName("키가 있는 제공자로 간편 로그인을 시작하면 Spring Security 의 인증 시작 주소로 302")
    void startOAuth() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/auth/oauth/google"))
                .andExpect(status().isFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Location", "/oauth2/authorization/google"));
    }

    @Test
    @DisplayName("로그인한 회원이 purpose=reauth 로 시작하면 본인 확인을 시작하고 같은 인증 시작 주소로 302")
    void startOAuthReauth() throws Exception {
        com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails me = new com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails(
                com.wanted.moneybeetle.domain.member.model.dto.LoginMemberDTO.from(
                        reauthMember(), com.wanted.moneybeetle.domain.member.model.entity.Member.ROLE_MEMBER));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(me, null, me.getAuthorities()));
        try {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/auth/oauth/google?purpose=reauth"))
                    .andExpect(status().isFound())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Location", "/oauth2/authorization/google"));

            verify(socialLoginService).beginReauth(org.mockito.ArgumentMatchers.eq(7L), any());
            verify(socialLoginService, never()).cancelReauth(any());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("로그인하지 않은 채 purpose=reauth 로 시작하면 401 이고 본인 확인을 시작하지 않는다")
    void startOAuthReauthRequiresLogin() throws Exception {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/auth/oauth/google?purpose=reauth"))
                .andExpect(status().isUnauthorized());

        verify(socialLoginService, never()).beginReauth(any(), any());
    }

    @Test
    @DisplayName("일반 간편 로그인을 시작하면 남아 있던 본인 확인 상태를 지운다")
    void startOAuthClearsPendingReauth() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/auth/oauth/google"))
                .andExpect(status().isFound());

        verify(socialLoginService).cancelReauth(any());
        verify(socialLoginService, never()).beginReauth(any(), any());
    }

    private static com.wanted.moneybeetle.domain.member.model.entity.Member reauthMember() {
        com.wanted.moneybeetle.domain.member.model.entity.Member member = com.wanted.moneybeetle.domain.member.model.entity.Member.signupSocial(
                "minji@gmail.com", "민지", java.time.LocalDate.of(1999, 4, 12), "FEMALE", java.time.LocalDate.of(2026, 11, 10));
        org.springframework.test.util.ReflectionTestUtils.setField(member, "memberId", 7L);
        return member;
    }

    @Test
    @DisplayName("서비스가 제공자를 쓸 수 없다고 하면 503 OAUTH_PROVIDER_UNAVAILABLE 이고 인증 시작 주소로 보내지 않는다")
    void startOAuthUnavailable() throws Exception {
        org.mockito.Mockito.doThrow(new com.wanted.moneybeetle.global.exception.BusinessException(
                        com.wanted.moneybeetle.domain.member.exception.MemberErrorCode.OAUTH_PROVIDER_UNAVAILABLE))
                .when(socialLoginService).requireAvailableProvider("kakao");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/auth/oauth/kakao"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("OAUTH_PROVIDER_UNAVAILABLE"));
    }

    @Test
    @DisplayName("처음 온 사람의 제공자 정보를 돌려준다 (제공자·이메일·이름)")
    void oauthSignupInfo() throws Exception {
        when(socialLoginService.getPending(any())).thenReturn(
                new com.wanted.moneybeetle.domain.auth.model.dto.SocialProfile("GOOGLE", "g-1", "minji@gmail.com", "민지"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/auth/oauth-signup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provider").value("GOOGLE"))
                .andExpect(jsonPath("$.data.email").value("minji@gmail.com"))
                .andExpect(jsonPath("$.data.nickname").value("민지"))
                .andExpect(jsonPath("$.data.providerUserId").doesNotExist());   // 제공자 쪽 사용자 ID 는 화면에 주지 않는다
    }

    @Test
    @DisplayName("세션에 가입 정보가 없으면 400 OAUTH_SESSION_EXPIRED")
    void oauthSignupInfoExpired() throws Exception {
        when(socialLoginService.getPending(any())).thenThrow(new BusinessException(MemberErrorCode.OAUTH_SESSION_EXPIRED));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/auth/oauth-signup"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("OAUTH_SESSION_EXPIRED"));
    }

    @Test
    @DisplayName("가입 정보를 보내면 201 로 가입하고 바로 로그인 상태를 만든다")
    void oauthSignup() throws Exception {
        com.wanted.moneybeetle.domain.member.model.entity.Member member =
                com.wanted.moneybeetle.domain.member.model.entity.Member.signupSocial("minji@gmail.com", "민지",
                        LocalDate.of(1999, 4, 12), "FEMALE", LocalDate.of(2026, 11, 10));
        org.springframework.test.util.ReflectionTestUtils.setField(member, "memberId", 42L);
        when(socialLoginService.completeSignup(any(), any())).thenReturn(
                com.wanted.moneybeetle.domain.member.model.dto.LoginMemberDTO.from(member, "MEMBER"));

        mockMvc.perform(post("/api/v1/auth/oauth-signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"민지\",\"birthDate\":\"1999-04-12\",\"gender\":\"FEMALE\",\"agreedTermsIds\":[1,2]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.memberId").value(42))
                .andExpect(jsonPath("$.data.nickname").value("민지"));

        verify(authSessionWriter).signIn(any(), any(), any());
    }

    @Test
    @DisplayName("가입이 거절되면(닉네임 중복 등) 로그인 상태를 만들지 않는다")
    void oauthSignupRejected() throws Exception {
        when(socialLoginService.completeSignup(any(), any())).thenThrow(new BusinessException(MemberErrorCode.NICKNAME_DUPLICATED));

        mockMvc.perform(post("/api/v1/auth/oauth-signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"민지\",\"birthDate\":\"1999-04-12\",\"agreedTermsIds\":[1]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NICKNAME_DUPLICATED"));

        verify(authSessionWriter, never()).signIn(any(), any(), any());
    }

    @Test
    @DisplayName("입력 형식이 틀린 가입 정보는 서비스를 부르지 않고 400")
    void oauthSignupInvalidBody() throws Exception {
        mockMvc.perform(post("/api/v1/auth/oauth-signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"!\",\"agreedTermsIds\":[]}"))
                .andExpect(status().isBadRequest());

        verify(socialLoginService, never()).completeSignup(any(), any());
    }
}
