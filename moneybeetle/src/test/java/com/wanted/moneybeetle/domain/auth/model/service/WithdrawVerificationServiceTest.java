package com.wanted.moneybeetle.domain.auth.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.domain.member.model.dto.LoginMemberDTO;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : DBA ② (회원·인증)
*   간편 로그인 회원의 탈퇴 본인 확인(이메일 코드, 이슈 #232) - 서버·DB·메일 서버 없이 가짜 MemberService·메일러와 세션으로 돈다
*  */
class WithdrawVerificationServiceTest {

    private static final String EMAIL = "minji@gmail.com";

    private final MemberService memberService = mock(MemberService.class);
    private final IdentityVerificationMailer mailer = mock(IdentityVerificationMailer.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-10T00:00:00Z"));
    private final MockHttpSession session = new MockHttpSession();
    private WithdrawVerificationService service;

    @BeforeEach
    void setUp() {
        service = new WithdrawVerificationService(memberService, mailer, clock);
        when(memberService.findLoginMemberById(7L)).thenReturn(Optional.of(socialMember(7L)));
        when(memberService.findLoginMemberById(8L)).thenReturn(Optional.of(socialMember(8L)));
    }

    private static LoginMemberDTO socialMember(Long memberId) {
        Member member = Member.signupSocial(EMAIL, "민지", LocalDate.of(1999, 4, 12), "FEMALE", LocalDate.of(2026, 11, 10));
        ReflectionTestUtils.setField(member, "memberId", memberId);
        return LoginMemberDTO.from(member, Member.ROLE_MEMBER);
    }

    private static LoginMemberDTO passwordMember() {
        Member member = Member.signup(EMAIL, "hash", "민지", LocalDate.of(1999, 4, 12), "FEMALE", LocalDate.of(2026, 11, 10));
        ReflectionTestUtils.setField(member, "memberId", 9L);
        return LoginMemberDTO.from(member, Member.ROLE_MEMBER);
    }

    /** 메일러에 넘어간 코드를 꺼낸다 (실제로는 메일로만 알 수 있는 값) */
    private String sentCode() {
        ArgumentCaptor<String> code = forClass(String.class);
        verify(mailer).send(eq(EMAIL), code.capture());
        return code.getValue();
    }

    private static void assertCode(Runnable action, com.wanted.moneybeetle.global.exception.ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    @Test
    @DisplayName("코드를 요청하면 가입 이메일로 6자리 숫자를 보내고, 코드를 확인하면 탈퇴 확인을 한 번만 쓸 수 있다")
    void requestVerifyAndConsumeOnce() {
        service.requestCode(7L, session);
        String code = sentCode();
        assertThat(code).matches("\\d{6}");

        service.verify(7L, code, session);

        assertThat(service.consumeVerified(7L, session)).isTrue();
        assertThat(service.consumeVerified(7L, session)).isFalse();   // 확인은 한 번만 쓸 수 있다
    }

    @Test
    @DisplayName("취소(API 17-3): 코드를 확인한 뒤 취소하면 탈퇴 확인을 쓸 수 없다")
    void cancelAfterVerifyBlocksConsume() {
        service.requestCode(7L, session);
        service.verify(7L, sentCode(), session);

        service.cancel(7L, session);

        assertThat(service.consumeVerified(7L, session)).isFalse();
    }

    @Test
    @DisplayName("취소: 받은 코드도 지워서 늦게 도착한 코드 확인이 취소를 되돌리지 못한다 (IDENTITY_CODE_EXPIRED)")
    void lateVerifyCannotUndoCancel() {
        service.requestCode(7L, session);
        String code = sentCode();

        service.cancel(7L, session);

        assertCode(() -> service.verify(7L, code, session), MemberErrorCode.IDENTITY_CODE_EXPIRED);
        assertThat(service.consumeVerified(7L, session)).isFalse();
    }

    @Test
    @DisplayName("취소: 지울 것이 없거나 세션이 없어도 오류가 아니고, 여러 번 불러도 같다")
    void cancelIsIdempotent() {
        service.cancel(7L, session);
        service.cancel(7L, null);

        service.requestCode(7L, session);
        service.cancel(7L, session);
        service.cancel(7L, session);

        assertThat(service.consumeVerified(7L, session)).isFalse();
    }

    @Test
    @DisplayName("취소: 이미 시작한 코드 요청이 취소 뒤에 코드를 저장·발송하지 못한다 (취소 횟수 비교). 예약한 발송 간격은 돌려준다")
    void inFlightRequestCannotUndoCancel() {
        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean(false);
        // 요청이 회원을 조회하는 사이(= 세션 잠금을 얻기 전)에 탈퇴 창이 닫혀 취소가 먼저 끝난 상황을 만든다
        when(memberService.findLoginMemberById(7L)).thenAnswer(invocation -> {
            if (cancelled.compareAndSet(false, true)) {
                service.cancel(7L, session);
            }
            return Optional.of(socialMember(7L));
        });

        assertCode(() -> service.requestCode(7L, session), MemberErrorCode.IDENTITY_CODE_EXPIRED);

        org.mockito.Mockito.verifyNoInteractions(mailer);                  // 코드를 보내지 않았다
        assertThat(service.consumeVerified(7L, session)).isFalse();
        // 예약한 60초 간격이 풀려서 바로 새로 요청할 수 있고, 취소 뒤 새로 시작한 요청은 정상으로 처리된다
        service.requestCode(7L, session);
        service.verify(7L, sentCode(), session);
        assertThat(service.consumeVerified(7L, session)).isTrue();
    }

    @Test
    @DisplayName("취소 뒤에도 새로 받은 코드로 처음부터 다시 확인할 수 있다 (60초 제한은 지킨다)")
    void canVerifyAgainAfterCancel() {
        service.requestCode(7L, session);
        service.cancel(7L, session);
        clock.advance(Duration.ofSeconds(61));

        service.requestCode(7L, session);
        ArgumentCaptor<String> code = forClass(String.class);
        verify(mailer, org.mockito.Mockito.times(2)).send(eq(EMAIL), code.capture());
        service.verify(7L, code.getValue(), session);

        assertThat(service.consumeVerified(7L, session)).isTrue();
    }

    @Test
    @DisplayName("코드를 확인하지 않았으면 탈퇴 확인을 쓸 수 없다 (코드만 받은 상태, 세션이 없는 상태 모두)")
    void notVerifiedCannotConsume() {
        assertThat(service.consumeVerified(7L, session)).isFalse();
        assertThat(service.consumeVerified(7L, null)).isFalse();

        service.requestCode(7L, session);
        assertThat(service.consumeVerified(7L, session)).isFalse();   // 받기만 하고 확인하지 않았다
    }

    @Test
    @DisplayName("다른 회원은 이 세션의 확인을 쓸 수 없다")
    void otherMemberCannotConsume() {
        service.requestCode(7L, session);
        service.verify(7L, sentCode(), session);

        when(memberService.findLoginMemberById(8L)).thenReturn(Optional.of(otherEmailMember(8L)));

        assertThat(service.consumeVerified(8L, session)).isFalse();
        assertThat(service.consumeVerified(7L, session)).isTrue();
    }

    private static LoginMemberDTO otherEmailMember(Long memberId) {
        Member member = Member.signupSocial("other@gmail.com", "다른", LocalDate.of(1999, 4, 12), "FEMALE", LocalDate.of(2026, 11, 10));
        ReflectionTestUtils.setField(member, "memberId", memberId);
        return LoginMemberDTO.from(member, Member.ROLE_MEMBER);
    }

    @Test
    @DisplayName("확인을 마친 뒤 5분이 지나면 쓸 수 없다")
    void verifiedExpires() {
        service.requestCode(7L, session);
        service.verify(7L, sentCode(), session);

        clock.advance(WithdrawVerificationService.VERIFIED_TTL);

        assertThat(service.consumeVerified(7L, session)).isFalse();
    }

    @Test
    @DisplayName("코드가 5분이 지나면 만료되고, 틀린 코드는 IDENTITY_CODE_INVALID 이며, 5번 틀리면 코드가 폐기된다")
    void wrongAndExpiredCode() {
        service.requestCode(7L, session);
        String code = sentCode();
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int i = 1; i < WithdrawVerificationService.MAX_FAILURES; i++) {
            assertCode(() -> service.verify(7L, wrong, session), MemberErrorCode.IDENTITY_CODE_INVALID);
        }
        assertCode(() -> service.verify(7L, wrong, session), MemberErrorCode.IDENTITY_CODE_EXPIRED);   // 5번째 실패: 폐기
        assertCode(() -> service.verify(7L, code, session), MemberErrorCode.IDENTITY_CODE_EXPIRED);    // 맞는 코드도 소용없다

        clock.advance(Duration.ofSeconds(61));
        service.requestCode(7L, session);
        clock.advance(WithdrawVerificationService.CODE_TTL);
        assertCode(() -> service.verify(7L, "123456", session), MemberErrorCode.IDENTITY_CODE_EXPIRED);
    }

    @Test
    @DisplayName("코드를 받은 적이 없으면 확인할 수 없다 (IDENTITY_CODE_EXPIRED)")
    void verifyWithoutRequest() {
        assertCode(() -> service.verify(7L, "123456", session), MemberErrorCode.IDENTITY_CODE_EXPIRED);
        assertCode(() -> service.verify(7L, "123456", null), MemberErrorCode.IDENTITY_CODE_EXPIRED);
    }

    @Test
    @DisplayName("같은 회원은 60초에 한 번만 코드를 받을 수 있다. 세션을 새로 만들어도 같다")
    void resendLimit() {
        service.requestCode(7L, session);

        assertCode(() -> service.requestCode(7L, session), MemberErrorCode.IDENTITY_CODE_TOO_SOON);
        assertCode(() -> service.requestCode(7L, new MockHttpSession()), MemberErrorCode.IDENTITY_CODE_TOO_SOON);

        clock.advance(WithdrawVerificationService.RESEND_INTERVAL);
        service.requestCode(7L, session);   // 60초 뒤에는 다시 받을 수 있다
    }

    @Test
    @DisplayName("다시 받은 코드가 오면 이전 코드는 쓸 수 없다")
    void newCodeReplacesOld() {
        service.requestCode(7L, session);
        ArgumentCaptor<String> codes = forClass(String.class);
        clock.advance(WithdrawVerificationService.RESEND_INTERVAL);
        service.requestCode(7L, session);
        verify(mailer, org.mockito.Mockito.times(2)).send(eq(EMAIL), codes.capture());
        String first = codes.getAllValues().get(0);
        String second = codes.getAllValues().get(1);

        if (!first.equals(second)) {
            assertCode(() -> service.verify(7L, first, session), MemberErrorCode.IDENTITY_CODE_INVALID);
        }
        service.verify(7L, second, session);
        assertThat(service.consumeVerified(7L, session)).isTrue();
    }

    @Test
    @DisplayName("비밀번호가 있는 회원은 이 방식을 쓸 수 없고(FORBIDDEN), 없는 회원이면 MEMBER_NOT_FOUND 이며 메일은 보내지 않는다")
    void onlyPasswordlessMembers() {
        when(memberService.findLoginMemberById(9L)).thenReturn(Optional.of(passwordMember()));
        when(memberService.findLoginMemberById(99L)).thenReturn(Optional.empty());

        assertCode(() -> service.requestCode(9L, session), CommonErrorCode.FORBIDDEN);
        assertCode(() -> service.verify(9L, "123456", session), CommonErrorCode.FORBIDDEN);
        assertCode(() -> service.requestCode(99L, session), MemberErrorCode.MEMBER_NOT_FOUND);

        org.mockito.Mockito.verifyNoInteractions(mailer);
    }

    @Test
    @DisplayName("메일 발송 대기열이 가득 차면 IDENTITY_MAIL_UNAVAILABLE 이고, 바로 다시 요청할 수 있으며, 이미 마친 확인은 그대로 둔다")
    void queueFullKeepsPreviousVerification() {
        service.requestCode(7L, session);
        service.verify(7L, sentCode(), session);
        clock.advance(WithdrawVerificationService.RESEND_INTERVAL);
        doThrow(new TaskRejectedException("full")).when(mailer).send(anyString(), anyString());

        assertCode(() -> service.requestCode(7L, session), MemberErrorCode.IDENTITY_MAIL_UNAVAILABLE);

        assertThat(service.consumeVerified(7L, session)).isTrue();   // 이미 마친 확인은 사라지지 않는다
        org.mockito.Mockito.reset(mailer);
        service.requestCode(7L, session);                            // 보낸 적 없는 요청은 제한에 걸리지 않는다
    }

    @Test
    @DisplayName("같은 세션으로 동시에 탈퇴 확인을 쓰려 해도 성공은 정확히 한 번이다")
    void concurrentConsumeSucceedsOnce() throws Exception {
        service.requestCode(7L, session);
        service.verify(7L, sentCode(), session);
        int threads = 8;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.List<java.util.concurrent.Future<Boolean>> results = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return service.consumeVerified(7L, session);
            }));
        }
        start.countDown();
        int successes = 0;
        for (java.util.concurrent.Future<Boolean> result : results) {
            if (result.get(10, java.util.concurrent.TimeUnit.SECONDS)) {
                successes++;
            }
        }
        pool.shutdown();

        assertThat(successes).isEqualTo(1);
    }

    @Test
    @DisplayName("확인을 쓰는 요청과 새 코드를 받는 요청이 동시에 와도, 새로 받은 코드의 상태는 지워지지 않는다")
    void consumeNeverWipesNewlyIssuedCode() throws Exception {
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 300; round++) {
                MockHttpSession roundSession = new MockHttpSession();
                clock.advance(WithdrawVerificationService.RESEND_INTERVAL);
                service.requestCode(7L, roundSession);
                org.mockito.ArgumentCaptor<String> codes = forClass(String.class);
                verify(mailer, org.mockito.Mockito.atLeastOnce()).send(eq(EMAIL), codes.capture());
                service.verify(7L, codes.getValue(), roundSession);
                clock.advance(WithdrawVerificationService.RESEND_INTERVAL);

                java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
                java.util.concurrent.Future<Boolean> consume = pool.submit(() -> {
                    start.await();
                    return service.consumeVerified(7L, roundSession);
                });
                java.util.concurrent.Future<?> request = pool.submit(() -> {
                    start.await();
                    service.requestCode(7L, roundSession);
                    return null;
                });
                start.countDown();
                consume.get(10, java.util.concurrent.TimeUnit.SECONDS);
                request.get(10, java.util.concurrent.TimeUnit.SECONDS);

                // 어떤 순서로 처리되든 새 코드 요청은 성공했으므로 세션에는 새로 받은 코드의 상태가 남아 있어야 한다
                assertThat(roundSession.getAttribute(WithdrawVerificationService.SESSION_KEY)).as("round " + round).isNotNull();
            }
        } finally {
            pool.shutdown();
        }
    }

    /** 시간을 앞으로 보낼 수 있는 시계 */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Seoul");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
