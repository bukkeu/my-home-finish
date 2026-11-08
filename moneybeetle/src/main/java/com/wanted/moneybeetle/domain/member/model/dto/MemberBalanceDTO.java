package com.wanted.moneybeetle.domain.member.model.dto;

import com.wanted.moneybeetle.domain.member.model.entity.Member;
import lombok.Getter;

/* comment.
*   담당 : DBA ② (회원·인증)
*   회원의 레벨·경험치·캐시 현재 값. growth.RewardService 가 Member Entity 를 직접 만지지 않고 이 값만 받는다 (도메인 분리).
*  */
@Getter
public class MemberBalanceDTO {

    private final Long memberId;
    private final int level;
    private final int exp;
    private final long cash;

    private MemberBalanceDTO(Long memberId, int level, int exp, long cash) {
        this.memberId = memberId;
        this.level = level;
        this.exp = exp;
        this.cash = cash;
    }

    public static MemberBalanceDTO from(Member member) {
        return new MemberBalanceDTO(member.getMemberId(), member.getLevel(), member.getExp(), member.getCash());
    }

    /** 테스트나 다른 도메인의 Fake 에서 값을 직접 만들 때 쓴다 */
    public static MemberBalanceDTO of(Long memberId, int level, int exp, long cash) {
        return new MemberBalanceDTO(memberId, level, exp, cash);
    }
}
