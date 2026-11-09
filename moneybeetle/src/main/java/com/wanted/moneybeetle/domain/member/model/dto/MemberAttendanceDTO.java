package com.wanted.moneybeetle.domain.member.model.dto;

import com.wanted.moneybeetle.domain.member.model.entity.Member;
import java.time.LocalDate;
import lombok.Getter;

/* comment.
*   담당 : DBA ② (회원·인증)
*   회원의 출석 상태 (growth.AttendanceService 용)
*   - newlyChecked : 이번 호출로 오늘 출석이 처음 기록됐는가
*   - streak       : 오늘 기준으로 이어지고 있는 연속 출석 일수 (끊겼으면 0)
*  */
@Getter
public class MemberAttendanceDTO {

    private final Long memberId;
    private final boolean newlyChecked;
    private final boolean checkedToday;
    private final int streak;

    private MemberAttendanceDTO(Long memberId, boolean newlyChecked, boolean checkedToday, int streak) {
        this.memberId = memberId;
        this.newlyChecked = newlyChecked;
        this.checkedToday = checkedToday;
        this.streak = streak;
    }

    public static MemberAttendanceDTO of(Member member, boolean newlyChecked, LocalDate today) {
        return new MemberAttendanceDTO(member.getMemberId(), newlyChecked,
                member.hasAttended(today), member.currentStreak(today));
    }

    /** 테스트와 다른 도메인의 테스트용 */
    public static MemberAttendanceDTO of(Long memberId, boolean newlyChecked, boolean checkedToday, int streak) {
        return new MemberAttendanceDTO(memberId, newlyChecked, checkedToday, streak);
    }
}
