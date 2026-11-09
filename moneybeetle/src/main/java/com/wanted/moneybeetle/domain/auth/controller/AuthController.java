package com.wanted.moneybeetle.domain.auth.controller;

import com.wanted.moneybeetle.domain.auth.model.dto.PasswordResetDTO;
import com.wanted.moneybeetle.domain.auth.model.service.PasswordResetService;
import com.wanted.moneybeetle.domain.member.model.dto.FindEmailDTO;
import com.wanted.moneybeetle.domain.member.model.dto.MaskedEmailDTO;
import com.wanted.moneybeetle.domain.member.model.service.AccountService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/* comment.
*   담당 : DBA ② (회원·인증)
*   POST /api/v1/auth/login, /logout, /admin/auth/login  (로그인·로그아웃은 Security 가 처리한다, #6·#33)
*   GET  /api/v1/auth/oauth/{provider}
*   POST /api/v1/auth/find-email, /password-reset/request, /password-reset/confirm   (#8)
*   POST /api/v1/auth/identity-verification
*   화면 : /login(LoginSelect), /login/user, /instructor/login, /admin/login
*  */
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AccountService accountService;
    private final PasswordResetService passwordResetService;

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
}
