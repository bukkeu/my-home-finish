package com.wanted.moneybeetle.domain.member.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/* comment.
*   담당 : DBA ② (회원·인증)
*   회원 탈퇴 요청 (API 17) { "password": "..." } - 본인 확인용 현재 비밀번호 (비밀번호가 없는 간편 로그인 회원은 생략)
*  */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawDTO {

    /** 비밀번호가 있는 회원만 보낸다. 간편 로그인 회원은 비워 두고 구글로 다시 로그인해 확인한다 */
    private String password;
}
