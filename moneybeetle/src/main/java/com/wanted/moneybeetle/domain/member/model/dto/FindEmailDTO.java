package com.wanted.moneybeetle.domain.member.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/* comment.
*   담당 : DBA ② (회원·인증)
*   아이디(이메일) 찾기 요청 (API 9) { "nickname": "민지", "birthDate": "1999-04-12" }
*  */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FindEmailDTO {

    @NotBlank(message = "닉네임을 입력해 주세요.")
    private String nickname;

    @NotNull(message = "생년월일을 입력해 주세요.")
    private LocalDate birthDate;
}
