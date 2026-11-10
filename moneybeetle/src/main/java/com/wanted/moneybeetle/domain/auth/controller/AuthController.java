package com.wanted.moneybeetle.domain.auth.controller;

import com.wanted.moneybeetle.domain.auth.model.dto.IdentityVerificationDTO;
import com.wanted.moneybeetle.domain.auth.handler.AuthSessionWriter;
import com.wanted.moneybeetle.domain.auth.model.dto.PasswordResetDTO;
import com.wanted.moneybeetle.domain.auth.model.dto.SocialProfile;
import com.wanted.moneybeetle.domain.auth.model.service.IdentityVerificationService;
import com.wanted.moneybeetle.domain.auth.model.service.PasswordResetService;
import com.wanted.moneybeetle.domain.auth.model.service.SocialLoginService;
import com.wanted.moneybeetle.domain.member.model.dto.FindEmailDTO;
import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.domain.member.model.dto.LoginMemberDTO;
import com.wanted.moneybeetle.domain.member.model.dto.MaskedEmailDTO;
import com.wanted.moneybeetle.domain.member.model.dto.SignupResultDTO;
import com.wanted.moneybeetle.domain.member.model.dto.SocialSignupDTO;
import com.wanted.moneybeetle.domain.member.model.service.AccountService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/* comment.
*   담당 : DBA ② (회원·인증)
*   POST /api/v1/auth/login, /logout, /admin/auth/login  (로그인·로그아웃은 Security 가 처리한다, #6·#33)
*   GET  /api/v1/auth/oauth/{provider}, GET·POST /api/v1/auth/oauth-signup   (#9, 간편 로그인: 구글·카카오 실제 OAuth)
*   POST /api/v1/auth/find-email, /password-reset/request, /password-reset/confirm   (#8)
*   POST /api/v1/auth/identity-verification/request, /identity-verification   (#9, 가입 중 이메일 코드 본인인증)
*   화면 : /login(LoginSelect), /login/user, /instructor/login, /admin/login
*  */
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AccountService accountService;
    private final PasswordResetService passwordResetService;
    private final IdentityVerificationService identityVerificationService;
    private final SocialLoginService socialLoginService;
    private final AuthSessionWriter authSessionWriter;

    // API 9. 아이디(이메일) 찾기
    @PostMapping("/api/v1/auth/find-email")
    public ApiResponse<MaskedEmailDTO> findEmail(@Valid @RequestBody FindEmailDTO request) {
        return ApiResponse.success(accountService.findMaskedEmail(request.getNickname(), request.getBirthDate()));
    }

    // API 10. 비밀번호 재설정 요청 (가입 여부와 상관없이 202)
    @PostMapping("/api/v1/auth/password-reset/request")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetDTO.Request request) {
        passwordResetService.requestReset(request.getEmail());
        return ResponseEntity.accepted().build();
    }

    // API 11. 비밀번호 재설정
    @PostMapping("/api/v1/auth/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetDTO.Confirm request) {
        passwordResetService.confirmReset(request.getToken(), request.getNewPassword(), request.getNewPasswordConfirm());
        return ResponseEntity.noContent().build();
    }

    // API 5-1. 본인인증 코드 요청 (이메일로 6자리 코드를 보낸다)
    @PostMapping("/api/v1/auth/identity-verification/request")
    public ResponseEntity<Void> requestIdentityCode(@Valid @RequestBody IdentityVerificationDTO.CodeRequest request,
                                                    HttpServletRequest httpRequest, HttpSession session) {
        identityVerificationService.requestCode(request.getEmail(), httpRequest.getRemoteAddr(), session);
        return ResponseEntity.accepted().build();
    }

    // API 5. 본인인증 (받은 코드 확인)
    @PostMapping("/api/v1/auth/identity-verification")
    public ApiResponse<IdentityVerificationDTO.Result> verifyIdentity(@Valid @RequestBody IdentityVerificationDTO.Verify request,
                                                                      HttpSession session) {
        identityVerificationService.verify(request.getEmail(), request.getCode(), session);
        return ApiResponse.success(IdentityVerificationDTO.Result.of(true));
    }

    // API 8. 간편 로그인 시작: 제공자(구글·카카오) 로그인 화면으로 보낸다. 키가 없는 제공자는 OAUTH_PROVIDER_UNAVAILABLE
    @GetMapping("/api/v1/auth/oauth/{provider}")
    public ResponseEntity<Void> startOAuth(@PathVariable String provider) {
        socialLoginService.requireAvailableProvider(provider);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/oauth2/authorization/" + provider)).build();
    }

    // API 8-1. 간편 로그인으로 처음 온 사람의 제공자 정보 (가입 화면이 이메일·이름을 미리 채운다)
    @GetMapping("/api/v1/auth/oauth-signup")
    public ApiResponse<SocialProfileView> oauthSignupInfo(HttpServletRequest request) {
        SocialProfile pending = socialLoginService.getPending(request.getSession(false));
        return ApiResponse.success(new SocialProfileView(pending.provider(), pending.email(), pending.nickname()));
    }

    // API 8-2. 간편 로그인으로 처음 온 사람의 가입 완료 (가입하면 바로 로그인된다)
    @PostMapping("/api/v1/auth/oauth-signup")
    public ResponseEntity<ApiResponse<SignupResultDTO>> oauthSignup(@Valid @RequestBody SocialSignupDTO request,
                                                                    HttpServletRequest httpRequest,
                                                                    HttpServletResponse httpResponse) {
        LoginMemberDTO member = socialLoginService.completeSignup(request, httpRequest.getSession(false));
        authSessionWriter.signIn(httpRequest, httpResponse, member);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(SignupResultDTO.of(member.getMemberId(), member.getNickname())));
    }

    /** API 8-1 응답 { provider, email, nickname } */
    public record SocialProfileView(String provider, String email, String nickname) {
    }
}
