package com.wanted.moneybeetle.domain.growth.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.growth.model.dao.AttendanceRepository;
import com.wanted.moneybeetle.domain.growth.model.dto.AttendanceResultDTO;
import com.wanted.moneybeetle.domain.growth.model.entity.Attendance;
import com.wanted.moneybeetle.domain.growth.model.entity.RewardRule;
import com.wanted.moneybeetle.domain.member.model.dao.InstructorProfileRepository;
import com.wanted.moneybeetle.domain.member.model.dao.MemberRepository;
import com.wanted.moneybeetle.domain.member.model.dao.MemberTermsAgreementRepository;
import com.wanted.moneybeetle.domain.member.model.dao.TermsRepository;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.operation.model.service.ForbiddenWordService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : PM (성장·홈·모의 청약)
*   결석하면 출석 경험치가 1배부터 다시 시작하는지 확인한다 (이슈 #205, SP-11·SP-22)
*   - AttendanceServiceTest 는 MemberService 를 가짜로 두고 연속 일수를 직접 넣어 주므로, "하루 빠지면 연속 일수가 1 로 돌아간다"는 경로는
*     거치지 않는다. 여기서는 실제 MemberService·Member(연속 출석 계산)를 쓰고 저장소·RewardService 만 가짜로 둬서,
*     회원의 기존 출석 기록과 결석을 만든 뒤 checkToday 를 부르고 결과의 연속 일수와 경험치 배수를 함께 확인한다. 서버·DB 없이 돈다.
*  */
class AttendanceStreakResetTest {

    private static final long MEMBER = 8L;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);

    private MemberRepository memberRepository;
    private AttendanceRepository attendanceRepository;
    private RewardService rewardService;
    private AttendanceService service;
    private Member member;

    @BeforeEach
    void setUp() {
        // 2026-11-08T15:30Z = 서울 2026-11-09 00:30 → 서울 기준 오늘은 11월 9일
        Clock clock = Clock.fixed(Instant.parse("2026-11-08T15:30:00Z"), ZoneId.of("Asia/Seoul"));
        memberRepository = mock(MemberRepository.class);
        MemberService memberService = new MemberService(memberRepository, mock(TermsRepository.class),
                mock(MemberTermsAgreementRepository.class), mock(PasswordEncoder.class), mock(ForbiddenWordService.class),
                clock, mock(InstructorProfileRepository.class));
        attendanceRepository = mock(AttendanceRepository.class);
        rewardService = mock(RewardService.class);
        service = new AttendanceService(attendanceRepository, memberService, rewardService, clock);

        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
            Attendance attendance = invocation.getArgument(0);
            ReflectionTestUtils.setField(attendance, "attendanceId", 500L);
            return attendance;
        });
        member = Member.signup("minji@gmail.com", "hash", "민지", LocalDate.of(1999, 4, 12), "FEMALE", TODAY);
        ReflectionTestUtils.setField(member, "memberId", MEMBER);
        when(memberRepository.findByIdForUpdate(MEMBER)).thenReturn(Optional.of(member));
        when(memberRepository.findById(MEMBER)).thenReturn(Optional.of(member));
    }

    /** 회원의 지난 출석 기록을 만든다: 연속 streak 일째까지 출석했고 마지막 출석이 lastAttended 였다 */
    private void givenAttendedUntil(LocalDate lastAttended, int streak) {
        ReflectionTestUtils.setField(member, "attendanceStreak", streak);
        ReflectionTestUtils.setField(member, "lastAttendedDate", lastAttended);
    }

    @Test
    @DisplayName("12일 연속 출석하다 하루를 빠지면(마지막 출석이 그저께) 오늘 연속 일수는 1 이고 출석 경험치 배수도 1 이다")
    void missingADayResetsStreakAndExpMultiplier() {
        givenAttendedUntil(TODAY.minusDays(2), 12);   // 어제 하루 결석

        AttendanceResultDTO result = service.checkToday(MEMBER);

        assertThat(result.getStreak()).isEqualTo(1);
        verify(rewardService).grantWithExpMultiplier(MEMBER, RewardRule.ATTENDANCE_DAILY, 500L, 1);
        verify(rewardService, never()).grant(anyLong(), eq(RewardRule.ATTENDANCE_DAILY), anyLong());
        assertThat(member.getAttendanceStreak()).isEqualTo(1);
        assertThat(member.getLastAttendedDate()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("오래 쉬었다가(한 달 전이 마지막) 와도 1 부터 다시 시작한다")
    void longAbsenceAlsoResets() {
        givenAttendedUntil(TODAY.minusDays(30), 25);

        AttendanceResultDTO result = service.checkToday(MEMBER);

        assertThat(result.getStreak()).isEqualTo(1);
        verify(rewardService).grantWithExpMultiplier(MEMBER, RewardRule.ATTENDANCE_DAILY, 500L, 1);
    }

    @Test
    @DisplayName("빠지지 않고 어제도 출석했으면 연속 일수가 이어져(12 → 13) 배수는 3 이다 (결석 경로와 비교하는 대조)")
    void consecutiveDayContinuesStreak() {
        givenAttendedUntil(TODAY.minusDays(1), 12);

        AttendanceResultDTO result = service.checkToday(MEMBER);

        assertThat(result.getStreak()).isEqualTo(13);
        verify(rewardService).grantWithExpMultiplier(MEMBER, RewardRule.ATTENDANCE_DAILY, 500L, 3);
    }

    @Test
    @DisplayName("결석으로 끊긴 뒤 처음 출석하는 날은 1배, 그 다음 날에도 5일째까지는 1배다 (끊긴 뒤 다시 쌓기 시작)")
    void restartsAtOneThenGrowsAgain() {
        givenAttendedUntil(TODAY.minusDays(3), 30);
        AttendanceResultDTO first = service.checkToday(MEMBER);
        assertThat(first.getStreak()).isEqualTo(1);
        verify(rewardService).grantWithExpMultiplier(MEMBER, RewardRule.ATTENDANCE_DAILY, 500L, 1);

        // 다음 날 (하루 지난 시계로 같은 회원을 다시 출석시킨다)
        Clock nextDay = Clock.fixed(Instant.parse("2026-11-09T15:30:00Z"), ZoneId.of("Asia/Seoul"));
        MemberService memberService = new MemberService(memberRepository, mock(TermsRepository.class),
                mock(MemberTermsAgreementRepository.class), mock(PasswordEncoder.class), mock(ForbiddenWordService.class),
                nextDay, mock(InstructorProfileRepository.class));
        RewardService nextDayRewards = mock(RewardService.class);
        AttendanceService nextDayService = new AttendanceService(attendanceRepository, memberService, nextDayRewards, nextDay);

        AttendanceResultDTO second = nextDayService.checkToday(MEMBER);

        assertThat(second.getStreak()).isEqualTo(2);
        verify(nextDayRewards).grantWithExpMultiplier(MEMBER, RewardRule.ATTENDANCE_DAILY, 500L, 1);
    }
}
