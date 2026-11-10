package com.wanted.moneybeetle.domain.member.model.dto;

import com.wanted.moneybeetle.domain.member.model.entity.Member;

/* comment.
*   담당 : DBA ① (사용자·강사 관리, #37)
*   API 101 사용자·강사 목록 한 줄
*   { "memberId": 1, "nickname": "민지", "email": "minji@gmail.com", "level": 4, "role": "MEMBER", "status": "ACTIVE", "cash": 12400 }
*  */
public record AdminMemberItemDTO(Long memberId, String nickname, String email, int level,
                                 String role, String status, long cash) {

    public static AdminMemberItemDTO from(Member member) {
        return new AdminMemberItemDTO(member.getMemberId(), member.getNickname(), member.getEmail(),
                member.getLevel(), member.getRole(), member.getStatus(), member.getCash());
    }
}
