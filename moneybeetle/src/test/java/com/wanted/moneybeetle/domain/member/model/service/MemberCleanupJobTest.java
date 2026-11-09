package com.wanted.moneybeetle.domain.member.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

class MemberCleanupJobTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 9, 3, 0);

    private MemberService memberService;
    private AccountService accountService;
    private MemberCleanupJob job;

    @BeforeEach
    void setUp() {
        memberService = mock(MemberService.class);
        accountService = mock(AccountService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-08T18:00:00Z"), ZoneId.of("Asia/Seoul"));   // 서울 2026-11-09 03:00
        job = new MemberCleanupJob(memberService, accountService, clock);
    }

    @Test
    @DisplayName("한 묶음(100명)을 다 채우면 다음 묶음을 이어서 처리하고, 100명보다 적게 처리되면 멈춘다")
    void processesBatchesUntilFewerThanBatchSize() {
        when(memberService.purgeWithdrawnMembers(NOW, 100)).thenReturn(100, 100, 30);

        job.purgeWithdrawnMembers();

        verify(memberService, times(3)).purgeWithdrawnMembers(NOW, 100);
    }

    @Test
    @DisplayName("지울 회원이 없으면 한 번만 부르고 끝낸다")
    void stopsImmediatelyWhenNothingToPurge() {
        when(memberService.purgeWithdrawnMembers(NOW, 100)).thenReturn(0);

        job.purgeWithdrawnMembers();

        verify(memberService, times(1)).purgeWithdrawnMembers(NOW, 100);
    }

    @Test
    @DisplayName("처리 수가 줄지 않는 이상 상황에서도 최대 묶음 수를 넘겨 끝없이 돌지 않는다")
    void neverLoopsForever() {
        when(memberService.purgeWithdrawnMembers(any(), anyInt())).thenReturn(100);

        job.purgeWithdrawnMembers();

        verify(memberService, times(MemberCleanupJob.MAX_BATCHES)).purgeWithdrawnMembers(NOW, 100);
    }

    @Test
    @DisplayName("회원 정리가 실패해도 예외를 밖으로 던지지 않고 토큰 삭제는 계속한다")
    void memberFailureDoesNotBlockTokenCleanup() {
        when(memberService.purgeWithdrawnMembers(any(), anyInt())).thenThrow(new IllegalStateException("db down"));
        when(accountService.deleteExpiredPasswordResetTokens(NOW)).thenReturn(4);

        job.runDaily();

        verify(accountService).deleteExpiredPasswordResetTokens(NOW);
    }

    @Test
    @DisplayName("토큰 삭제가 실패해도 예외를 밖으로 던지지 않는다")
    void tokenFailureIsContained() {
        when(accountService.deleteExpiredPasswordResetTokens(any())).thenThrow(new IllegalStateException("db down"));

        job.runDaily();

        verify(memberService).purgeWithdrawnMembers(eq(NOW), eq(100));
    }

    @Test
    @DisplayName("예약 시각 기본값(매일 03:00)은 올바른 크론식이고 서울 시간으로 3시를 가리킨다")
    void defaultCronIsValid() {
        CronExpression cron = CronExpression.parse("0 0 3 * * *");

        LocalDateTime next = cron.next(LocalDateTime.of(2026, 11, 9, 3, 0));
        assertThat(next).isEqualTo(LocalDateTime.of(2026, 11, 10, 3, 0));
    }
}
