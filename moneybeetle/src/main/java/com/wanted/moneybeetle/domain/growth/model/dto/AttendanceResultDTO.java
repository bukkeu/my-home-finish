package com.wanted.moneybeetle.domain.growth.model.dto;

import lombok.Getter;

/* comment.
*   담당 : PM (성장·홈·모의 청약)
*   오늘 출석을 기록한 결과 (로그인 응답 API 6 의 attendance)
*   { checkedToday, streak, rewardCash } 에 이번에 처음 기록했는지(newlyChecked)를 더했다.
*   rewardCash 는 이번 출석으로 받은 캐시다 (연속 5일째부터. 이미 오늘 출석했거나 5일 전이면 0).
*  */
@Getter
public class AttendanceResultDTO {

    private final boolean checkedToday;
    private final boolean newlyChecked;
    private final int streak;
    private final long rewardCash;

    private AttendanceResultDTO(boolean newlyChecked, int streak, long rewardCash) {
        this.checkedToday = true;
        this.newlyChecked = newlyChecked;
        this.streak = streak;
        this.rewardCash = rewardCash;
    }

    /** 오늘 처음 출석을 기록했다 */
    public static AttendanceResultDTO checked(int streak, long rewardCash) {
        return new AttendanceResultDTO(true, streak, rewardCash);
    }

    /** 오늘 이미 출석해서 아무것도 바꾸지 않았다 */
    public static AttendanceResultDTO alreadyChecked(int streak) {
        return new AttendanceResultDTO(false, streak, 0);
    }
}
