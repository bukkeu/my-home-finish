package com.wanted.moneybeetle.domain.auth.model.dto;

import com.wanted.moneybeetle.domain.growth.model.dto.AttendanceResultDTO;
import lombok.Getter;

/* comment.
*   담당 : DBA ② (회원·인증)
*   로그인 성공 응답 (API 6) { memberId, nickname, role, redirectUrl, attendance }
*   - attendance : 일반 회원 로그인에서 오늘 출석을 기록한 결과 { checkedToday, newlyChecked, streak, rewardCash }.
*     강사·관리자이거나 출석 기록이 실패했으면 null 이다 (로그인은 그대로 성공)
*  */
@Getter
public class LoginResultDTO {

    private final Long memberId;
    private final String nickname;
    private final String role;
    private final String redirectUrl;
    private final AttendanceResultDTO attendance;

    public LoginResultDTO(Long memberId, String nickname, String role, String redirectUrl,
                          AttendanceResultDTO attendance) {
        this.memberId = memberId;
        this.nickname = nickname;
        this.role = role;
        this.redirectUrl = redirectUrl;
        this.attendance = attendance;
    }
}
