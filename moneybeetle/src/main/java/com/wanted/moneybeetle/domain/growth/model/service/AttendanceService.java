package com.wanted.moneybeetle.domain.growth.model.service;

import com.wanted.moneybeetle.domain.growth.model.dao.AttendanceRepository;
import com.wanted.moneybeetle.domain.growth.model.dto.AttendanceResultDTO;
import com.wanted.moneybeetle.domain.growth.model.dto.AttendanceStatusDTO;
import com.wanted.moneybeetle.domain.growth.model.entity.Attendance;
import com.wanted.moneybeetle.domain.growth.model.entity.RewardRule;
import com.wanted.moneybeetle.domain.member.model.dto.MemberAttendanceDTO;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* comment.
*   담당 : PM (성장·홈·모의 청약)
*   출석 기록(KST), 연속 출석 계산, 5일째부터 캐시 지급 (SP-10~12, 이슈 #12)
*   - checkToday(memberId) : 오늘 출석 기록 [호출: 로그인 성공 처리(AuthSuccessHandler)]. 하루에 한 번만 기록된다
*   - getStatus(memberId)  : 출석 현황 (API 21, 메뉴의 연속 출석 표시 HOM-05)
*   - 연속 출석 일수는 회원 테이블(member.attendance_streak·last_attended_date)에 두고 MemberService 로만 바꾼다.
*     출석한 날마다 attendance 에 한 줄을 남기고, 연속 5일째부터는 그 출석 ID 로 RewardService.grant 를 불러 캐시를 준다.
*   - 모두 한 트랜잭션이다. 캐시 지급이 실패하면 출석 기록도 함께 되돌려져, 다음 접속 때 다시 시도된다.
*   - 단, 보상 규칙이 꺼져 있거나 없으면(지급액 0) 지급을 건너뛰고 출석만 기록한다. 규칙을 끈다고 연속 출석이 끊기거나
*     (나중에 로그인에 연결했을 때) 로그인이 실패하지 않게 하려는 것이다. 이 사이에 규칙이 꺼지는 아주 짧은 경우만 지급 예외가 남는다.
*   - '오늘'은 공통 Clock(서울)으로 판단한다 (DP-42)
*  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceService {

    /** 연속 이 일수째부터 매일 캐시를 준다 (SP-12) */
    public static final int REWARD_START_STREAK = 5;

    private final AttendanceRepository attendanceRepository;
    private final MemberService memberService;
    private final RewardService rewardService;
    private final Clock clock;

    /** 오늘 출석을 기록하고, 연속 5일째부터는 연속 출석 캐시를 준다. 오늘 이미 출석했으면 아무것도 바꾸지 않는다 */
    @Transactional
    public AttendanceResultDTO checkToday(Long memberId) {
        LocalDate today = LocalDate.now(clock);
        MemberAttendanceDTO state = memberService.recordAttendance(memberId, today);
        if (!state.isNewlyChecked()) {
            return AttendanceResultDTO.alreadyChecked(state.getStreak());
        }

        Attendance attendance = attendanceRepository.save(Attendance.create(memberId, today, state.getStreak()));

        long rewardCash = 0;
        // 관리자가 연속 출석 보상 규칙을 꺼 두었으면(사용 중인 규칙 없음) 보상만 건너뛴다. 출석 기록과 연속 일수는 그대로 쌓여서,
        // 규칙을 다시 켜도 연속 일수가 끊기지 않는다 (규칙이 꺼진 동안 지나간 날의 보상은 소급하지 않는다)
        if (state.getStreak() >= REWARD_START_STREAK
                && rewardService.findActiveCash(RewardRule.ATTENDANCE_STREAK) > 0) {
            // 원인 ID = 출석 ID 라서 같은 날 출석으로는 두 번 지급되지 않는다 (SP-18)
            rewardCash = rewardService
                    .grant(memberId, RewardRule.ATTENDANCE_STREAK, attendance.getAttendanceId())
                    .getGrantedCash();
        }
        return AttendanceResultDTO.checked(state.getStreak(), rewardCash);
    }

    /** 출석 현황 (API 21) */
    public AttendanceStatusDTO getStatus(Long memberId) {
        MemberAttendanceDTO state = memberService.getAttendance(memberId, LocalDate.now(clock));
        int daysToReward = Math.max(0, REWARD_START_STREAK - state.getStreak());
        long rewardCash = rewardService.findActiveCash(RewardRule.ATTENDANCE_STREAK);
        return new AttendanceStatusDTO(state.getStreak(), daysToReward, rewardCash, state.isCheckedToday());
    }
}
