package com.wanted.moneybeetle.domain.member.model.dto;

import com.wanted.moneybeetle.domain.member.model.policy.SignupRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/* comment.
*   담당 : DBA ② (회원·인증)
*   비밀번호 변경 요청 (API 16) - 새 비밀번호 형식은 가입과 같은 규칙(SP-03)이다.
*   현재 비밀번호가 맞는지, 새 비밀번호와 확인이 같은지는 Service 에서 순서대로 확인한다.
*  */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PasswordChangeDTO {

    @NotBlank(message = "현재 비밀번호를 입력해 주세요.")
    private String currentPassword;

    @NotBlank(message = "새 비밀번호를 입력해 주세요.")
    @Pattern(regexp = SignupRules.PASSWORD_PATTERN, message = "비밀번호는 8~20자의 영문과 숫자를 모두 포함해야 해요.")
    private String newPassword;

    @NotBlank(message = "새 비밀번호를 한 번 더 입력해 주세요.")
    private String newPasswordConfirm;
}
