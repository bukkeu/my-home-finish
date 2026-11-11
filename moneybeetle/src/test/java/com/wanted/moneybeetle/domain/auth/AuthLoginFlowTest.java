package com.wanted.moneybeetle.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanted.moneybeetle.domain.auth.handler.AuthFailHandler;
import com.wanted.moneybeetle.domain.auth.handler.AuthSuccessHandler;
import com.wanted.moneybeetle.domain.auth.model.service.AuthService;
import com.wanted.moneybeetle.domain.member.model.dao.InstructorProfileRepository;
import com.wanted.moneybeetle.domain.member.model.dao.MemberRepository;
import com.wanted.moneybeetle.domain.member.model.dao.TermsRepository;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.member.model.entity.InstructorProfile;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.domain.member.model.entity.MemberStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import com.wanted.moneybeetle.domain.auth.model.service.LoginAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : DBA ② (회원·인증)
*   로그인 흐름(이슈 #6) - 진짜 DaoAuthenticationProvider + AuthService + 성공·실패 핸들러를 이어서 돌린다.
*   회원 저장소만 가짜(Mock)로 두므로 서버·DB 없이 돌고, 5회 실패 잠금(1분, 이후 5분)·해제 같은 시간 규칙은 움직이는 시계로 확인한다.
*   Spring Security 가 UserDetailsService 의 예외를 어떻게 감싸는지도 실제 객체로 확인한다.
*  */
class AuthLoginFlowTest {

    /** 테스트가 시간을 앞으로 보낼 수 있는 시계 */
    static class MutableClock extends Clock {
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

    private static final String EMAIL = "minji@gmail.com";
    private static final String PASSWORD = "beetle1234";

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MutableClock clock;
    private MemberRepository memberRepository;
    private InstructorProfileRepository instructorProfileRepository;
    private AuthenticationManager authenticationManager;
    private AuthFailHandler failHandler;
    private org.springframework.transaction.PlatformTransactionManager txManager;
    private AuthSuccessHandler successHandler;
    private Member member;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-09T00:00:00Z"));
        memberRepository = mock(MemberRepository.class);
        instructorProfileRepository = mock(InstructorProfileRepository.class);
        givenMember(Member.ROLE_MEMBER, Member.STATUS_ACTIVE);

        AuthService authService = new AuthService(
                new MemberService(memberRepository, mock(TermsRepository.class),
                        mock(com.wanted.moneybeetle.domain.member.model.dao.MemberTermsAgreementRepository.class),
                        encoder, mock(com.wanted.moneybeetle.domain.operation.model.service.ForbiddenWordService.class),
                        clock, instructorProfileRepository, mock(com.wanted.moneybeetle.domain.member.model.dao.SocialAccountRepository.class)), clock);
        txManager = mock(org.springframework.transaction.PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(mock(org.springframework.transaction.TransactionStatus.class));
        LoginAuthenticationProvider provider = new LoginAuthenticationProvider(authService, encoder, txManager);
        authenticationManager = new ProviderManager(provider);
        failHandler = new AuthFailHandler(objectMapper);
        successHandler = new AuthSuccessHandler(objectMapper, mock(com.wanted.moneybeetle.domain.growth.model.service.AttendanceService.class));
    }

    private void givenMember(String role, String status) {
        member = Member.signup(EMAIL, encoder.encode(PASSWORD), "민지", LocalDate.of(1999, 4, 12), "FEMALE",
                LocalDate.of(2026, 11, 9));
        ReflectionTestUtils.setField(member, "memberId", 8L);
        ReflectionTestUtils.setField(member, "role", role);
        ReflectionTestUtils.setField(member, "status", MemberStatus.valueOf(status));
        when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.of(member));
        when(memberRepository.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(member));
        when(memberRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(member));
        when(memberRepository.findByEmail(org.mockito.ArgumentMatchers.argThat(e -> !EMAIL.equals(e))))
                .thenReturn(Optional.empty());
        when(memberRepository.findByEmailForUpdate(org.mockito.ArgumentMatchers.argThat(e -> !EMAIL.equals(e))))
                .thenReturn(Optional.empty());
    }

    /** 로그인 시도 한 번: 성공이면 성공 핸들러, 실패면 실패 핸들러가 쓴 응답을 돌려준다 */
    private record Outcome(int status, JsonNode json, Authentication authentication) {
        String errorCode() {
            return json.path("error").path("code").asText();
        }
    }

    private Outcome login(String email, String password) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setParameter("email", email);
        return login(request, password);
    }

    private Outcome login(MockHttpServletRequest request, String password) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        try {
            Authentication auth = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.getParameter("email"), password));
            successHandler.onAuthenticationSuccess(request, response, auth);
            return new Outcome(response.getStatus(), objectMapper.readTree(response.getContentAsString()), auth);
        } catch (AuthenticationException e) {
            failHandler.onAuthenticationFailure(request, response, e);
            return new Outcome(response.getStatus(), objectMapper.readTree(response.getContentAsString()), null);
        }
    }

    @Test
    @DisplayName("올바른 이메일·비밀번호면 200 과 회원 정보·이동할 화면을 돌려주고 마지막 로그인 시각을 남긴다 (이메일은 대소문자 구분 없음)")
    void loginSuccess() throws Exception {
        Outcome outcome = login("  MinJi@Gmail.com ", PASSWORD);

        assertThat(outcome.status()).isEqualTo(200);
        assertThat(outcome.json().path("success").asBoolean()).isTrue();
        assertThat(outcome.json().path("data").path("memberId").asLong()).isEqualTo(8L);
        assertThat(outcome.json().path("data").path("nickname").asText()).isEqualTo("민지");
        assertThat(outcome.json().path("data").path("role").asText()).isEqualTo("MEMBER");
        assertThat(outcome.json().path("data").path("redirectUrl").asText()).isEqualTo("/user/home");
        assertThat(member.getLastLoginAt()).isNotNull();
        // 마지막 로그인 기록은 회원 행을 잠그는 조회로 한다 (동시 실패 기록과 서로 덮어쓰지 않게)
        org.mockito.Mockito.verify(memberRepository).findByIdForUpdate(8L);
        org.mockito.Mockito.verify(memberRepository, org.mockito.Mockito.never()).findById(8L);
        assertThat(outcome.authentication().getAuthorities()).extracting(Object::toString).containsExactly("MEMBER");
    }

    @Test
    @DisplayName("비밀번호가 틀리면 401 LOGIN_FAILED, 없는 이메일도 똑같이 응답해 계정 존재 여부를 숨기고 실패 횟수는 올리지 않는다")
    void wrongPasswordAndUnknownEmail() throws Exception {
        Outcome wrong = login(EMAIL, "wrong-password1");
        Outcome unknown = login("nobody@gmail.com", PASSWORD);
        Outcome blank = login("", PASSWORD);

        assertThat(wrong.status()).isEqualTo(401);
        assertThat(wrong.errorCode()).isEqualTo("LOGIN_FAILED");
        assertThat(unknown.status()).isEqualTo(401);
        assertThat(unknown.errorCode()).isEqualTo("LOGIN_FAILED");
        assertThat(blank.errorCode()).isEqualTo("LOGIN_FAILED");
        assertThat(wrong.json().path("error").path("message").asText())
                .isEqualTo(unknown.json().path("error").path("message").asText());
        assertThat(member.getFailedLoginCount()).isEqualTo(1);   // 틀린 비밀번호 한 번만
    }

    @Test
    @DisplayName("연속 5회 틀리면 5번째 응답부터 423 LOGIN_LOCKED 이고, 잠금 중에는 맞는 비밀번호도 거절한다 (비밀번호 검사 전에 잠금 확인)")
    void lockAfterFiveFailures() throws Exception {
        for (int i = 1; i <= 4; i++) {
            assertThat(login(EMAIL, "wrong-password1").errorCode()).isEqualTo("LOGIN_FAILED");
        }
        Outcome fifth = login(EMAIL, "wrong-password1");
        assertThat(fifth.status()).isEqualTo(423);
        assertThat(fifth.errorCode()).isEqualTo("LOGIN_LOCKED");
        assertThat(member.getFailedLoginCount()).isEqualTo(5);

        Outcome correctWhileLocked = login(EMAIL, PASSWORD);
        assertThat(correctWhileLocked.status()).isEqualTo(423);
        assertThat(correctWhileLocked.errorCode()).isEqualTo("LOGIN_LOCKED");
        assertThat(member.getFailedLoginCount()).isEqualTo(5);   // 잠금 중 시도는 횟수를 더 올리지 않는다
    }

    @Test
    @DisplayName("첫 잠금은 1분이다: 59초까지는 잠금, 1분이 지나면 풀려 로그인되고 실패 횟수가 0 으로 돌아간다")
    void firstLockReleasedAfterOneMinute() throws Exception {
        for (int i = 0; i < 5; i++) {
            login(EMAIL, "wrong-password1");
        }

        clock.advance(Duration.ofSeconds(59));
        assertThat(login(EMAIL, PASSWORD).errorCode()).isEqualTo("LOGIN_LOCKED");

        clock.advance(Duration.ofSeconds(1));
        Outcome success = login(EMAIL, PASSWORD);
        assertThat(success.status()).isEqualTo(200);
        assertThat(member.getFailedLoginCount()).isZero();
        assertThat(member.getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("잠금이 풀린 뒤 5회를 더 틀리면(누적 10회) 이번에는 5분 잠근다: 4분 59초까지 잠금, 5분이 지나면 풀린다")
    void secondLockIsFiveMinutes() throws Exception {
        for (int i = 0; i < 5; i++) {
            login(EMAIL, "wrong-password1");
        }
        clock.advance(Duration.ofMinutes(1));

        // 풀린 직후: 실패 횟수는 5 로 남아 있고, 이어서 틀리면 6 번째다
        for (int i = 1; i <= 4; i++) {
            assertThat(login(EMAIL, "wrong-password1").errorCode()).isEqualTo("LOGIN_FAILED");
        }
        Outcome tenth = login(EMAIL, "wrong-password1");
        assertThat(tenth.status()).isEqualTo(423);
        assertThat(member.getFailedLoginCount()).isEqualTo(10);

        clock.advance(Duration.ofMinutes(4).plusSeconds(59));
        assertThat(login(EMAIL, PASSWORD).errorCode()).isEqualTo("LOGIN_LOCKED");

        clock.advance(Duration.ofSeconds(1));
        assertThat(login(EMAIL, PASSWORD).status()).isEqualTo(200);
        assertThat(member.getFailedLoginCount()).isZero();
    }

    @Test
    @DisplayName("그 뒤에도 5회 실패마다 5분씩 잠근다 (15회째)")
    void laterLocksStayFiveMinutes() throws Exception {
        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < 5; i++) {
                login(EMAIL, "wrong-password1");
            }
            clock.advance(Duration.ofMinutes(round == 0 ? 1 : 5));   // 해당 잠금이 풀릴 때까지
        }
        // 09:00 첫 잠금(1분) -> 09:01 에 5회 더, 5분 잠금 -> 09:06 에 5회 더(누적 15회), 다시 5분 잠금 -> 09:11 까지
        assertThat(member.getFailedLoginCount()).isEqualTo(15);
        assertThat(member.getLockedUntil()).isEqualTo(java.time.LocalDateTime.of(2026, 11, 9, 9, 11));
    }

    @Test
    @DisplayName("잠금이 풀린 뒤 한 번 틀려도 횟수를 이어서 센다 (6회째), 맞게 로그인하면 처음부터 다시 센다")
    void failureCountContinuesAfterLockExpires() throws Exception {
        for (int i = 0; i < 5; i++) {
            login(EMAIL, "wrong-password1");
        }
        clock.advance(Duration.ofMinutes(1));

        Outcome sixth = login(EMAIL, "wrong-password1");
        assertThat(sixth.errorCode()).isEqualTo("LOGIN_FAILED");
        assertThat(member.getFailedLoginCount()).isEqualTo(6);

        assertThat(login(EMAIL, PASSWORD).status()).isEqualTo(200);
        assertThat(member.getFailedLoginCount()).isZero();
        for (int i = 0; i < 5; i++) {
            login(EMAIL, "wrong-password1");
        }
        // 성공 뒤라 다시 첫 잠금(1분)이다
        assertThat(member.getLockedUntil()).isEqualTo(java.time.LocalDateTime.of(2026, 11, 9, 9, 2));
    }

    @Test
    @DisplayName("4번 틀린 뒤 맞게 로그인하면 실패 횟수가 0 으로 돌아간다 (연속 실패만 센다)")
    void successResetsFailureCount() throws Exception {
        for (int i = 0; i < 4; i++) {
            login(EMAIL, "wrong-password1");
        }
        assertThat(login(EMAIL, PASSWORD).status()).isEqualTo(200);
        assertThat(member.getFailedLoginCount()).isZero();

        assertThat(login(EMAIL, "wrong-password1").errorCode()).isEqualTo("LOGIN_FAILED");   // 다시 1회부터
        assertThat(member.getFailedLoginCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("정지 계정은 403 ACCOUNT_SUSPENDED, 탈퇴 계정은 403 ACCOUNT_WITHDRAWN 이고 실패 횟수는 올리지 않는다")
    void suspendedAndWithdrawnAccounts() throws Exception {
        givenMember(Member.ROLE_MEMBER, Member.STATUS_SUSPENDED);
        Outcome suspended = login(EMAIL, PASSWORD);
        assertThat(suspended.status()).isEqualTo(403);
        assertThat(suspended.errorCode()).isEqualTo("ACCOUNT_SUSPENDED");

        givenMember(Member.ROLE_MEMBER, Member.STATUS_WITHDRAWN);
        Outcome withdrawn = login(EMAIL, PASSWORD);
        assertThat(withdrawn.status()).isEqualTo(403);
        assertThat(withdrawn.errorCode()).isEqualTo("ACCOUNT_WITHDRAWN");
        assertThat(member.getFailedLoginCount()).isZero();
    }

    @Test
    @DisplayName("승인된 강사는 INSTRUCTOR 권한, 승인 전·반려·정보 없는 강사는 MEMBER 권한으로 로그인된다 (SP-07)")
    void instructorNeedsApproval() throws Exception {
        givenMember(Member.ROLE_INSTRUCTOR, Member.STATUS_ACTIVE);

        InstructorProfile approved = InstructorProfile.create(8L, "민지 강사", "청약");
        ReflectionTestUtils.setField(approved, "approvalStatus", InstructorProfile.APPROVED);
        when(instructorProfileRepository.findByMemberId(8L)).thenReturn(Optional.of(approved));
        Outcome ok = login(EMAIL, PASSWORD);
        assertThat(ok.json().path("data").path("role").asText()).isEqualTo("INSTRUCTOR");
        assertThat(ok.json().path("data").path("redirectUrl").asText()).isEqualTo("/instructor/dashboard");

        InstructorProfile pending = InstructorProfile.create(8L, "민지 강사", "청약");
        when(instructorProfileRepository.findByMemberId(8L)).thenReturn(Optional.of(pending));
        assertThat(login(EMAIL, PASSWORD).json().path("data").path("role").asText()).isEqualTo("MEMBER");

        ReflectionTestUtils.setField(pending, "approvalStatus", InstructorProfile.REVOKED);
        assertThat(login(EMAIL, PASSWORD).json().path("data").path("role").asText()).isEqualTo("MEMBER");

        when(instructorProfileRepository.findByMemberId(8L)).thenReturn(Optional.empty());
        assertThat(login(EMAIL, PASSWORD).json().path("data").path("role").asText()).isEqualTo("MEMBER");
    }

    @Test
    @DisplayName("로그인 한 번은 하나의 트랜잭션이고, 실패해도 실패 기록은 커밋된다 (롤백하지 않는다)")
    void everyAttemptIsOneCommittedTransaction() throws Exception {
        login(EMAIL, "wrong-password1");   // 실패
        login(EMAIL, PASSWORD);            // 성공

        org.mockito.Mockito.verify(txManager, org.mockito.Mockito.times(2)).getTransaction(any());
        org.mockito.Mockito.verify(txManager, org.mockito.Mockito.times(2)).commit(any());
        org.mockito.Mockito.verify(txManager, org.mockito.Mockito.never()).rollback(any());
        assertThat(member.getFailedLoginCount()).isZero();   // 실패 뒤 성공이라 0 으로 돌아가 있다
    }

    @Test
    @DisplayName("이번 실패로 5회째가 되면 비밀번호 오류가 아니라 잠금(423)으로 응답한다 (같은 트랜잭션 안에서 잠금까지 기록)")
    void fifthFailureIsReportedAsLockedInSameTransaction() throws Exception {
        for (int i = 0; i < 4; i++) {
            login(EMAIL, "wrong-password1");
        }
        Outcome fifth = login(EMAIL, "wrong-password1");

        assertThat(fifth.status()).isEqualTo(423);
        assertThat(member.getLockedUntil()).isNotNull();
        org.mockito.Mockito.verify(txManager, org.mockito.Mockito.never()).rollback(any());
    }

    @Test
    @DisplayName("loadUserByUsername 는 인증 예외(잠금·정지·탈퇴·없는 이메일)로 트랜잭션을 롤백 전용으로 만들지 않는다 (실제 DB 에서 UnexpectedRollbackException 이 났던 문제)")
    void loadUserDoesNotMarkTransactionRollbackOnly() throws NoSuchMethodException {
        var tx = AuthService.class.getMethod("loadUserByUsername", String.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class);

        assertThat(tx).isNotNull();
        assertThat(tx.noRollbackFor()).contains(org.springframework.security.core.AuthenticationException.class);
    }

    @Test
    @DisplayName("로그인 기록 메서드는 읽기 전용이 아닌 쓰기 트랜잭션이다 (클래스가 읽기 전용이라, 열린 트랜잭션에 참여하는 MemberService 의 저장이 사라지지 않게)")
    void recordMethodsAreWriteTransactions() throws NoSuchMethodException {
        var failure = AuthService.class.getMethod("recordLoginFailure", String.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        var success = AuthService.class.getMethod("recordLoginSuccess", Long.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class);

        assertThat(failure).isNotNull();
        assertThat(failure.readOnly()).isFalse();
        assertThat(success).isNotNull();
        assertThat(success.readOnly()).isFalse();
    }

    @Test
    @DisplayName("이동할 화면: 안전한 next 가 먼저, 바깥 주소로 보내는 next 는 무시하고 역할별 첫 화면으로 간다")
    void redirectUrlFromNext() throws Exception {
        MockHttpServletRequest safe = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        safe.setParameter("email", EMAIL);
        safe.setParameter("next", "/user/lectures/3?tab=quiz");
        assertThat(login(safe, PASSWORD).json().path("data").path("redirectUrl").asText())
                .isEqualTo("/user/lectures/3?tab=quiz");

        for (String bad : new String[] {"https://evil.example", "//evil.example", "/\\evil.example", "/\t/evil.example"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            request.setParameter("email", EMAIL);
            request.setParameter("next", bad);
            assertThat(login(request, PASSWORD).json().path("data").path("redirectUrl").asText())
                    .as(bad).isEqualTo("/user/home");
        }
    }

    @Test
    @DisplayName("첫 잠금 시각은 서울 기준 지금 + 1분이다")
    void lockedUntilIsOneMinuteFromNow() throws Exception {
        for (int i = 0; i < 5; i++) {
            login(EMAIL, "wrong-password1");
        }

        // 시계: 2026-11-09T00:00:00Z = 서울 09:00
        assertThat(member.getLockedUntil()).isEqualTo(java.time.LocalDateTime.of(2026, 11, 9, 9, 1));
    }
}
