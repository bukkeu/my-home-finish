package com.wanted.moneybeetle.domain.member.model.dto;

import com.wanted.moneybeetle.domain.member.model.entity.Member;
import lombok.Getter;

/* comment.
*   담당 : DBA ② (회원·인증)
*   회원가입 응답 (API 4) 201 Created { "memberId": 1, "nickname": "민지" }
*  */
@Getter
public class SignupResultDTO {

    private final Long memberId;
    private final String nickname;

    private SignupResultDTO(Long memberId, String nickname) {
        this.memberId = memberId;
        this.nickname = nickname;
    }

    public static SignupResultDTO from(Member member) {
        return new SignupResultDTO(member.getMemberId(), member.getNickname());
    }
}
