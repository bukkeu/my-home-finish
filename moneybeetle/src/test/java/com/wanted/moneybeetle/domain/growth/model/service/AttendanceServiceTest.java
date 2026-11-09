package com.wanted.moneybeetle.domain.growth.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.growth.exception.GrowthErrorCode;
import com.wanted.moneybeetle.domain.growth.model.dao.AttendanceRepository;
import com.wanted.moneybeetle.domain.growth.model.dto.AttendanceResultDTO;
import com.wanted.moneybeetle.domain.growth.model.dto.AttendanceStatusDTO;
import com.wanted.moneybeetle.domain.growth.model.dto.RewardResultDTO;
import com.wanted.moneybeetle.domain.growth.model.entity.Attendance;
import com.wanted.moneybeetle.domain.growth.model.entity.RewardRule;
import com.wanted.moneybeetle.domain.member.model.dto.MemberAttendanceDTO;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/* comment.
*   담당 : PM (성장·홈·모의 청약)
*   AttendanceService (이슈 #12) - Repository·MemberService·RewardService 를 Mock 으로 두고 서버·DB 없이 돈다
*   '오늘'은 고정한 서울 시계로 판단한다 (DP-42)
*  */
class AttendanceServiceTest {

    private static final long MEMBER = 8L;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);

    private AttendanceRepository attendanceRepository;
    private MemberService memberService;
    private RewardService rewardService;
    private AttendanceService service;

    @BeforeEach
    void setUp() {
        attendanceRepository = mock(AttendanceRepository.class);
        memberService = mock(MemberService.class);
        rewardService = mock(RewardService.class);
        // 2026-11-08T15:30Z = 서울 2026-11-09 00:30 (UTC 로는 아직 전날이지만 서울 기준 오늘은 11월 9일)
        Clock clock = Clock.fixed(Instant.parse("2026-11-08T15:30:00Z"), ZoneId.of("Asia/Seoul"));
        service = new AttendanceService(attendanceRepository, memberService, rewardService, clock);

        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
            Attendance attendance = invocation.getArgument(0);
            ReflectionTestUtils.setField(attendance, "attendanceId", 500L);
            return attendance;
        });
    }

    private void givenRecorded(int streak, boolean newlyChecked) {
        when(memberService.recordAttendance(MEMBER, TODAY))
                .thenReturn(MemberAttendanceDTO.of(MEMBER, newlyChecked, true, streak));
    }

    @Test
    @DisplayName("오늘 처음 출석하면 서울 오늘 날짜로 한 줄 남기고, 5일 전이라 캐시는 주지 않는다")
    void firstCheckInRecordsWithoutReward() {
        givenRecorded(1, true);

        AttendanceResultDTO result = service.checkToday(MEMBER);

        assertThat(result.isCheckedToday()).isTrue();
        assertThat(result.isNewlyChecked()).isTrue();
        assertThat(result.getStreak()).isEqualTo(1);
        assertThat(result.getRewardCash()).isZero();
        ArgumentCaptor<Attendance> saved = ArgumentCaptor.forClass(Attendance.class);
        verify(attendanceRepository).save(saved.capture());
        assertThat(saved.getValue().getMemberId()).isEqualTo(MEMBER);
        assertThat(saved.getValue().getAttendDate()).isEqualTo(TODAY);
        assertThat(saved.getValue().getStreakCount()).isEqualTo(1);
        verify(rewardService, never()).grant(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("연속 4일째까지는 캐시가 없고, 5일째부터 그날의 출석 ID로 연속 출석 보상을 지급한다 (SP-12)")
    void rewardStartsAtFifthDay() {
        givenRecorded(4, true);
        service.checkToday(MEMBER);
        verify(rewardService, never()).grant(anyLong(), any(), anyLong());

        givenRecorded(5, true);
        when(rewardService.findActiveCash(RewardRule.ATTENDANCE_STREAK)).thenReturn(10_000L);
        when(rewardService.grant(MEMBER, RewardRule.ATTENDANCE_STREAK, 500L))
                .thenReturn(RewardResultDTO.granted(0, 10_000, 0, 10_000, 0, 1, 1, List.of()));

        AttendanceResultDTO result = service.checkToday(MEMBER);

        assertThat(result.getStreak()).isEqualTo(5);
        assertThat(result.getRewardCash()).isEqualTo(10_000);
        verify(rewardService).grant(MEMBER, RewardRule.ATTENDANCE_STREAK, 500L);
    }

    @Test
    @DisplayName("5일 넘게 이어져도 매일 지급한다 (6일째)")
    void rewardEveryDayAfterFifth() {
        givenRecorded(6, true);
        when(rewardService.findActiveCash(RewardRule.ATTENDANCE_STREAK)).thenReturn(10_000L);
        when(rewardService.grant(eq(MEMBER), eq(RewardRule.ATTENDANCE_STREAK), anyLong()))
                .thenReturn(RewardResultDTO.granted(0, 10_000, 0, 20_000, 0, 1, 1, List.of()));

        assertThat(service.checkToday(MEMBER).getRewardCash()).isEqualTo(10_000);
    }

    @Test
    @DisplayName("오늘 이미 출석했으면 기록도 지급도 하지 않고 현재 연속 일수만 돌려준다")
    void alreadyCheckedToday() {
        givenRecorded(7, false);

        AttendanceResultDTO result = service.checkToday(MEMBER);

        assertThat(result.isCheckedToday()).isTrue();
        assertThat(result.isNewlyChecked()).isFalse();
        assertThat(result.getStreak()).isEqualTo(7);
        assertThat(result.getRewardCash()).isZero();
        verify(attendanceRepository, never()).save(any());
        verify(rewardService, never()).grant(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("보상 규칙이 꺼져 있으면 지급만 건너뛰고 출석은 기록한다 - 연속 일수가 끊기지 않고 예외도 나지 않는다")
    void rewardRuleOffSkipsRewardButKeepsAttendance() {
        givenRecorded(5, true);
        when(rewardService.findActiveCash(RewardRule.ATTENDANCE_STREAK)).thenReturn(0L);

        AttendanceResultDTO result = service.checkToday(MEMBER);

        assertThat(result.getStreak()).isEqualTo(5);
        assertThat(result.isNewlyChecked()).isTrue();
        assertThat(result.getRewardCash()).isZero();
        verify(attendanceRepository).save(any());
        verify(rewardService, never()).grant(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("보상 지급이 실패하면 예외를 그대로 던진다 (같은 트랜잭션이라 출석 기록도 함께 되돌려진다)")
    void rewardFailurePropagates() {
        givenRecorded(5, true);
        when(rewardService.findActiveCash(RewardRule.ATTENDANCE_STREAK)).thenReturn(10_000L);
        when(rewardService.grant(MEMBER, RewardRule.ATTENDANCE_STREAK, 500L))
                .thenThrow(new BusinessException(GrowthErrorCode.REWARD_RULE_NOT_FOUND));

        assertThatThrownBy(() -> service.checkToday(MEMBER))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(GrowthErrorCode.REWARD_RULE_NOT_FOUND));
    }

    @Test
    @DisplayName("checkToday 는 읽기 전용이 아닌 하나의 트랜잭션이다")
    void checkTodayIsOneWriteTransaction() throws NoSuchMethodException {
        Transactional tx = AttendanceService.class.getMethod("checkToday", Long.class).getAnnotation(Transactional.class);

        assertThat(tx).isNotNull();
        assertThat(tx.readOnly()).isFalse();
    }

    @Test
    @DisplayName("출석 현황: 연속 3일이고 오늘 출석했으면 보상까지 2번, 보상 금액은 규칙 값 (명세서 API 21 예시)")
    void statusWithStreakThree() {
        when(memberService.getAttendance(MEMBER, TODAY)).thenReturn(MemberAttendanceDTO.of(MEMBER, false, true, 3));
        when(rewardService.findActiveCash(RewardRule.ATTENDANCE_STREAK)).thenReturn(10_000L);

        AttendanceStatusDTO status = service.getStatus(MEMBER);

        assertThat(status.getStreak()).isEqualTo(3);
        assertThat(status.getDaysToReward()).isEqualTo(2);
        assertThat(status.getRewardCash()).isEqualTo(10_000);
        assertThat(status.isCheckedToday()).isTrue();
    }

    @Test
    @DisplayName("출석 현황: 끊겼으면 0일에 보상까지 5번, 이미 받는 구간(5일 이상)이면 0번, 규칙이 꺼져 있으면 금액 0")
    void statusEdgeCases() {
        when(rewardService.findActiveCash(RewardRule.ATTENDANCE_STREAK)).thenReturn(0L);

        when(memberService.getAttendance(MEMBER, TODAY)).thenReturn(MemberAttendanceDTO.of(MEMBER, false, false, 0));
        AttendanceStatusDTO broken = service.getStatus(MEMBER);
        assertThat(broken.getDaysToReward()).isEqualTo(5);
        assertThat(broken.isCheckedToday()).isFalse();
        assertThat(broken.getRewardCash()).isZero();

        when(memberService.getAttendance(MEMBER, TODAY)).thenReturn(MemberAttendanceDTO.of(MEMBER, false, true, 9));
        assertThat(service.getStatus(MEMBER).getDaysToReward()).isZero();
    }
}
