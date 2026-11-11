package com.wanted.moneybeetle.domain.member.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/* comment.
*   담당 : DBA ② (회원·인증)
*   탈퇴 본인 확인 코드 확인 요청 (API 17-2) { "code": "123456" }
*  */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawVerificationDTO {

    @NotBlank(message = "인증번호를 입력해 주세요.")
    @Pattern(regexp = "\\d{6}", message = "인증번호는 숫자 6자리예요.")
    private String code;
}
