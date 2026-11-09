package com.wanted.moneybeetle.domain.auth.model.dto;

import com.wanted.moneybeetle.domain.member.model.policy.SignupRules;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/* comment.
*   담당 : DBA ② (회원·인증)
*   비밀번호 재설정 요청/확인 값 (API 10·11)
*   - Request : 재설정 메일을 받을 이메일 (email)
*   - Confirm : 메일로 받은 토큰과 새 비밀번호 (token, newPassword, newPasswordConfirm)
*  */
public class PasswordResetDTO {

    private PasswordResetDTO() {
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {

        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 올바르지 않아요.")
        private String email;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Confirm {

        @NotBlank(message = "인증 정보가 필요해요.")
        private String token;

        @NotBlank(message = "새 비밀번호를 입력해 주세요.")
        @Pattern(regexp = SignupRules.PASSWORD_PATTERN, message = "비밀번호는 8~20자의 영문과 숫자를 모두 포함해야 해요.")
        private String newPassword;

        @NotBlank(message = "새 비밀번호를 한 번 더 입력해 주세요.")
        private String newPasswordConfirm;
    }
}
