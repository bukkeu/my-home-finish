package com.wanted.moneybeetle.domain.auth.model.dto;

import lombok.Getter;

/* comment.
*   담당 : DBA ② (회원·인증)
*   로그인 성공 응답 (API 6) { memberId, nickname, role, redirectUrl }
*   - 명세서에는 attendance(checkedToday, streak, rewardCash)도 있다. 출석 서비스(#12)가 생기면 여기에 더한다
*  */
@Getter
public class LoginResultDTO {

    private final Long memberId;
    private final String nickname;
    private final String role;
    private final String redirectUrl;

    public LoginResultDTO(Long memberId, String nickname, String role, String redirectUrl) {
        this.memberId = memberId;
        this.nickname = nickname;
        this.role = role;
        this.redirectUrl = redirectUrl;
    }
}
