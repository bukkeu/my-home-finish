package com.wanted.moneybeetle.domain.member.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/* comment.
*   담당 : DBA ② (회원·인증)
*   회원 탈퇴 요청 (API 17) { "password": "..." } - 본인 확인용 현재 비밀번호
*  */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawDTO {

    @NotBlank(message = "비밀번호를 입력해 주세요.")
    private String password;
}
