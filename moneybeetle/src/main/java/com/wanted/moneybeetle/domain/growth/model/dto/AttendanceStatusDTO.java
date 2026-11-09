package com.wanted.moneybeetle.domain.growth.model.dto;

import lombok.Getter;

/* comment.
*   담당 : PM (성장·홈·모의 청약)
*   출석 현황 (API 21) { streak, daysToReward, rewardCash, checkedToday }
*   - streak       : 오늘 기준으로 이어지고 있는 연속 출석 일수 (하루라도 빠졌으면 0)
*   - daysToReward : 캐시를 받기까지 더 출석해야 하는 횟수 (이미 받는 구간이면 0)
*   - rewardCash   : 연속 출석 보상으로 하루에 주는 캐시 (보상 규칙의 값, 규칙이 꺼져 있으면 0)
*  */
@Getter
public class AttendanceStatusDTO {

    private final int streak;
    private final int daysToReward;
    private final long rewardCash;
    private final boolean checkedToday;

    public AttendanceStatusDTO(int streak, int daysToReward, long rewardCash, boolean checkedToday) {
        this.streak = streak;
        this.daysToReward = daysToReward;
        this.rewardCash = rewardCash;
        this.checkedToday = checkedToday;
    }
}
