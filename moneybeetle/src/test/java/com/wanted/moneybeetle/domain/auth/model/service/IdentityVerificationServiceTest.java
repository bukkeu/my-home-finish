package com.wanted.moneybeetle.domain.auth.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.domain.member.model.dto.AvailabilityDTO;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.mock.web.MockHttpSession;

/* comment.
*   담당 : DBA ② (회원·인증)
*   가입 중 본인인증(이메일 코드, 이슈 #9) - 서버·DB·메일 서버 없이 가짜 MemberService·메일러와 세션으로 돈다
*  */
class IdentityVerificationServiceTest {

    private static final String EMAIL = "minji@gmail.com";
    private static final String IP = "203.0.113.7";

    private final MemberService memberService = mock(MemberService.class);
    private final IdentityVerificationMailer mailer = mock(IdentityVerificationMailer.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-10T00:00:00Z"));
    private final MockHttpSession session = new MockHttpSession();
    private IdentityVerificationService service;

    @BeforeEach
    void setUp() {
        service = new IdentityVerificationService(memberService, mailer, clock);
        when(memberService.isEmailAvailable(anyString())).thenReturn(AvailabilityDTO.of(true));
    }

    /** 메일러에 넘어간 코드를 꺼낸다 (실제로는 메일로만 알 수 있는 값) */
    private String sentCode() {
        ArgumentCaptor<String> code = forClass(String.class);
        verify(mailer).send(eq(EMAIL), code.capture());
        return code.getValue();
    }

    @Test
    @DisplayName("코드를 요청하면 6자리 숫자를 메일로 보내고, 세션에는 코드 원문 없이 해시만 둔다")
    void requestSendsSixDigitCode() {
        service.requestCode("Minji@Gmail.com", IP, session);   // 대소문자는 정규화해 소문자로 보낸다

        String code = sentCode();
        assertThat(code).matches("\\d{6}");
        var state = (com.wanted.moneybeetle.domain.auth.model.dto.IdentityVerificationState)
                session.getAttribute(IdentityVerificationService.SESSION_KEY);
        assertThat(state).isNotNull();
        assertThat(state.getCodeHash()).isNotEqualTo(code).hasSize(64);   // 코드 원문이 아니라 SHA-256 해시(16진수 64자)
    }

    @Test
    @DisplayName("이메일 형식이 틀리면 EMAIL_INVALID 이고 메일을 보내지 않는다")
    void invalidEmail() {
        assertThatThrownBy(() -> service.requestCode("not-an-email", IP, session))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.EMAIL_INVALID));
        verify(mailer, never()).send(anyString(), anyString());
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 EMAIL_DUPLICATED 이고 메일을 보내지 않는다")
    void duplicatedEmail() {
        when(memberService.isEmailAvailable(anyString())).thenReturn(AvailabilityDTO.of(false));

        assertThatThrownBy(() -> service.requestCode(EMAIL, IP, session))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.EMAIL_DUPLICATED));
        verify(mailer, never()).send(anyString(), anyString());
    }

    @Test
    @DisplayName("같은 이메일로 60초 안에 다시 요청하면 IDENTITY_CODE_TOO_SOON, 60초가 지나면 다시 보낸다")
    void resendInterval() {
        service.requestCode(EMAIL, IP, session);

        clock.advance(Duration.ofSeconds(59));
        assertThatThrownBy(() -> service.requestCode(EMAIL, IP, session))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.IDENTITY_CODE_TOO_SOON));

        clock.advance(Duration.ofSeconds(1));
        assertThatCode(() -> service.requestCode(EMAIL, IP, session)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("세션을 바꿔도 같은 이메일의 60초 제한은 그대로 지켜진다 (남의 이메일로 메일을 마구 보내는 일을 막는다)")
    void resendIntervalAppliesAcrossSessions() {
        service.requestCode(EMAIL, IP, session);

        assertThatThrownBy(() -> service.requestCode(EMAIL, IP, new MockHttpSession()))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.IDENTITY_CODE_TOO_SOON));
    }

    @Test
    @DisplayName("한 접속 주소가 이메일을 바꿔 가며 1분에 10번을 넘게 요청하면 IDENTITY_REQUEST_LIMITED 이고, 메일도 보내지 않고 DB 도 보지 않는다")
    void ipLimit() {
        for (int i = 0; i < IdentityVerificationService.IP_LIMIT; i++) {
            service.requestCode("user" + i + "@gmail.com", IP, new MockHttpSession());
        }
        org.mockito.Mockito.clearInvocations(memberService, mailer);

        assertThatThrownBy(() -> service.requestCode("another@gmail.com", IP, new MockHttpSession()))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.IDENTITY_REQUEST_LIMITED));
        verify(mailer, never()).send(anyString(), anyString());
        verify(memberService, never()).isEmailAvailable(anyString());
    }

    @Test
    @DisplayName("접속 주소 제한은 주소마다 따로 세고, 1분이 지나면 다시 요청할 수 있다")
    void ipLimitIsPerAddressAndExpires() {
        for (int i = 0; i < IdentityVerificationService.IP_LIMIT; i++) {
            service.requestCode("user" + i + "@gmail.com", IP, new MockHttpSession());
        }

        assertThatCode(() -> service.requestCode("other-ip@gmail.com", "198.51.100.9", new MockHttpSession())).doesNotThrowAnyException();   // 다른 주소는 그대로

        clock.advance(IdentityVerificationService.IP_WINDOW);
        assertThatCode(() -> service.requestCode("later@gmail.com", IP, new MockHttpSession())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("접속 주소를 알 수 없으면(null·공백) 하나의 묶음으로 세어 같은 제한을 받는다")
    void unknownIpIsLimitedTogether() {
        for (int i = 0; i < IdentityVerificationService.IP_LIMIT; i++) {
            service.requestCode("user" + i + "@gmail.com", i % 2 == 0 ? null : " ", new MockHttpSession());
        }

        assertThatThrownBy(() -> service.requestCode("another@gmail.com", null, new MockHttpSession()))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.IDENTITY_REQUEST_LIMITED));
    }

    @Test
    @DisplayName("메일 발송 대기열이 가득 차면 IDENTITY_MAIL_UNAVAILABLE 이고, 바로 다시 요청할 수 있다")
    void mailQueueFull() {
        doThrow(new TaskRejectedException("full")).when(mailer).send(anyString(), anyString());

        assertThatThrownBy(() -> service.requestCode(EMAIL, IP, session))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.IDENTITY_MAIL_UNAVAILABLE));
        assertThat(session.getAttribute(IdentityVerificationService.SESSION_KEY)).isNull();

        org.mockito.Mockito.reset(mailer);   // 대기열이 비었다
        assertThatCode(() -> service.requestCode(EMAIL, IP, session)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("맞는 코드를 넣으면 인증된 것으로 표시되고, 가입 전 확인(requireVerified)을 통과한다")
    void verifyWithCorrectCode() {
        service.requestCode(EMAIL, IP, session);

        service.verify(EMAIL, sentCode(), session);

        assertThatCode(() -> service.requireVerified("MINJI@gmail.com", session)).doesNotThrowAnyException();   // 대소문자 구분 없이
    }

    @Test
    @DisplayName("틀린 코드는 IDENTITY_CODE_INVALID 이고, 그 뒤 맞는 코드로 인증할 수 있다")
    void wrongCodeThenCorrect() {
        service.requestCode(EMAIL, IP, session);
        String code = sentCode();
        String wrong = code.equals("000000") ? "111111" : "000000";

        assertThatThrownBy(() -> service.verify(EMAIL, wrong, session))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.IDENTITY_CODE_INVALID));

        assertThatCode(() -> service.verify(EMAIL, code, session)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("5번 틀리면 그 코드는 버려져 맞는 코드를 넣어도 IDENTITY_CODE_EXPIRED 이고 다시 받아야 한다")
    void tooManyFailures() {
        service.requestCode(EMAIL, IP, session);
        String code = sentCode();
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int i = 1; i < IdentityVerificationService.MAX_FAILURES; i++) {
            assertThatThrownBy(() -> service.verify(EMAIL, wrong, session))
                    .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.IDENTITY_CODE_INVALID));
        }
        assertThatThrownBy(() -> service.verify(EMAIL, wrong, session))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.IDENTITY_CODE_EXPIRED));

        assertThatThrownBy(() -> service.verify(EMAIL, code, session))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.IDENTITY_CODE_EXPIRED));
    }

    @Test
    @DisplayName("5분이 지난 코드는 IDENTITY_CODE_EXPIRED")
    void codeExpires() {
        service.requestCode(EMAIL, IP, session);
        String code = sentCode();

        clock.advance(IdentityVerificationService.CODE_TTL);

        assertThatThrownBy(() -> service.verify(EMAIL, code, session))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.IDENTITY_CODE_EXPIRED));
    }

    @Test
    @DisplayName("코드를 받은 적이 없거나 다른 이메일로 인증하려 하면 IDENTITY_CODE_EXPIRED")
    void noRequestOrOtherEmail() {
        assertThatThrownBy(() -> service.verify(EMAIL, "123456", session))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.IDENTITY_CODE_EXPIRED));

        service.requestCode(EMAIL, IP, session);
        String code = sentCode();
        assertThatThrownBy(() -> service.verify("other@gmail.com", code, session))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.IDENTITY_CODE_EXPIRED));
    }

    @Test
    @DisplayName("인증하지 않았거나 다른 이메일이면 가입 전 확인이 IDENTITY_NOT_VERIFIED 로 막는다")
    void requireVerifiedBlocks() {
        assertThatThrownBy(() -> service.requireVerified(EMAIL, session))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.IDENTITY_NOT_VERIFIED));

        service.requestCode(EMAIL, IP, session);   // 코드만 받고 아직 인증하지 않았다
        assertThatThrownBy(() -> service.requireVerified(EMAIL, session))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.IDENTITY_NOT_VERIFIED));

        service.verify(EMAIL, sentCode(), session);
        assertThatThrownBy(() -> service.requireVerified("other@gmail.com", session))   // 인증한 이메일과 다른 이메일로는 가입할 수 없다
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.IDENTITY_NOT_VERIFIED));
    }

    @Test
    @DisplayName("인증 표시는 30분이 지나면 쓸 수 없고, 가입이 끝나 clear 하면 지워진다")
    void verifiedExpiresAndClears() {
        service.requestCode(EMAIL, IP, session);
        service.verify(EMAIL, sentCode(), session);

        clock.advance(IdentityVerificationService.VERIFIED_TTL);
        assertThatThrownBy(() -> service.requireVerified(EMAIL, session))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.IDENTITY_NOT_VERIFIED));

        clock.advance(Duration.ofSeconds(-1));   // 만료 직전으로 되돌리면 다시 유효하다
        assertThatCode(() -> service.requireVerified(EMAIL, session)).doesNotThrowAnyException();

        service.clear(session);
        assertThatThrownBy(() -> service.requireVerified(EMAIL, session))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.IDENTITY_NOT_VERIFIED));
    }

    @Test
    @DisplayName("코드를 다시 요청하면 이전에 받은 코드는 쓸 수 없다")
    void newCodeReplacesOld() {
        service.requestCode(EMAIL, IP, session);
        ArgumentCaptor<String> codes = forClass(String.class);
        verify(mailer).send(eq(EMAIL), codes.capture());
        String first = codes.getValue();

        clock.advance(IdentityVerificationService.RESEND_INTERVAL);
        service.requestCode(EMAIL, IP, session);
        ArgumentCaptor<String> all = forClass(String.class);
        verify(mailer, org.mockito.Mockito.times(2)).send(eq(EMAIL), all.capture());
        String second = all.getAllValues().get(1);

        if (!first.equals(second)) {   // 우연히 같은 코드가 나오면 비교할 수 없다
            assertThatThrownBy(() -> service.verify(EMAIL, first, session))
                    .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.IDENTITY_CODE_INVALID));
        }
        assertThatCode(() -> service.verify(EMAIL, second, session)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("이미 5번 틀린 상태를 먼저 읽고 들어온 늦은 요청은 맞는 코드여도 비교하지 않고 IDENTITY_CODE_EXPIRED (제한 확인이 비교보다 앞선다)")
    void limitIsCheckedBeforeComparing() {
        service.requestCode(EMAIL, IP, session);
        String code = sentCode();
        var state = (com.wanted.moneybeetle.domain.auth.model.dto.IdentityVerificationState)
                session.getAttribute(IdentityVerificationService.SESSION_KEY);
        // 동시에 들어온 다른 요청들이 5번 틀린 것으로 센 뒤, 상태를 먼저 읽어 둔 이 요청이 뒤늦게 비교 단계에 온 상황을 흉내 낸다
        // (세션에서 상태가 지워지기 전이라 상태 객체는 그대로 남아 있다)
        for (int i = 0; i < IdentityVerificationService.MAX_FAILURES; i++) {
            state.recordFailure();
        }

        assertThatThrownBy(() -> service.verify(EMAIL, code, session))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.IDENTITY_CODE_EXPIRED));
        assertThatThrownBy(() -> service.requireVerified(EMAIL, session))   // 인증된 것으로 표시되지 않았다
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.IDENTITY_NOT_VERIFIED));
    }

    @Test
    @DisplayName("같은 세션에서 틀린 코드가 동시에 몰려도 5번까지만 비교한다 (동시 요청으로 시도 횟수 제한을 넘길 수 없다)")
    void concurrentGuessesAreLimited() throws Exception {
        // 여러 스레드가 같은 세션을 쓰므로 스레드에 안전한 가짜 세션을 쓴다 (MockHttpSession 의 저장소는 스레드에 안전하지 않다)
        java.util.Map<String, Object> store = new java.util.concurrent.ConcurrentHashMap<>();
        jakarta.servlet.http.HttpSession shared = mock(jakarta.servlet.http.HttpSession.class);
        org.mockito.Mockito.doAnswer(call -> store.put(call.getArgument(0), call.getArgument(1)))
                .when(shared).setAttribute(anyString(), org.mockito.ArgumentMatchers.any());
        when(shared.getAttribute(anyString())).thenAnswer(call -> store.get((String) call.getArgument(0)));
        org.mockito.Mockito.doAnswer(call -> store.remove((String) call.getArgument(0)))
                .when(shared).removeAttribute(anyString());

        service.requestCode(EMAIL, IP, shared);
        String code = sentCode();
        String wrong = code.equals("000000") ? "111111" : "000000";

        int threads = 40;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.List<java.util.concurrent.Future<MemberErrorCode>> results = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    service.verify(EMAIL, wrong, shared);
                    return null;
                } catch (BusinessException e) {
                    return (MemberErrorCode) e.getErrorCode();
                }
            }));
        }
        start.countDown();
        int invalid = 0;
        for (java.util.concurrent.Future<MemberErrorCode> result : results) {
            if (result.get(10, java.util.concurrent.TimeUnit.SECONDS) == MemberErrorCode.IDENTITY_CODE_INVALID) {
                invalid++;
            }
        }
        pool.shutdown();

        // 틀렸다는 답(= 코드를 실제로 비교한 요청)은 4번뿐이고, 5번째 이후는 모두 만료로 거절된다
        assertThat(invalid).isEqualTo(IdentityVerificationService.MAX_FAILURES - 1);
        assertThatThrownBy(() -> service.verify(EMAIL, code, shared))   // 제한에 걸린 뒤에는 맞는 코드도 쓸 수 없다
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.IDENTITY_CODE_EXPIRED));
    }

    /** 시간을 앞뒤로 옮길 수 있는 테스트용 시계 */
    private static final class MutableClock extends java.time.Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Seoul");
        }

        @Override
        public java.time.Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
