package com.wanted.moneybeetle.domain.auth.model.service;

import com.wanted.moneybeetle.domain.auth.model.dto.IdentityVerificationState;
import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.domain.member.model.dto.LoginMemberDTO;
import com.wanted.moneybeetle.domain.member.model.policy.SignupRules;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.member.model.service.WithdrawVerifier;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import jakarta.servlet.http.HttpSession;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.web.util.WebUtils;

/* comment.
*   담당 : DBA ② (회원·인증)
*   비밀번호가 없는 간편 로그인 회원의 탈퇴 본인 확인 - 가입 이메일로 6자리 코드를 보내고 맞는지 확인한다 (이슈 #232, API 17-1·17-2)
*   - 가입 중 본인인증(IdentityVerificationService)과 같은 규칙(코드 5분, 5번 틀리면 폐기)을 쓰되, 받는 사람은 요청한 로그인 회원의
*     가입 이메일로 정해져 있다 (요청으로 이메일을 받지 않는다). 가입용의 "이미 가입된 이메일 거절"과 접속 주소별 횟수 제한은 없다 (로그인한 회원 본인에게만 보낸다).
*   - 진행 상태(코드 해시·만료·틀린 횟수·인증 완료 표시)는 HTTP 세션에 담고 DB 테이블은 만들지 않는다. 같은 회원은 60초에 한 번만 코드를 받을 수 있다.
*   - 확인을 마치면 5분 동안 탈퇴(AccountService.withdraw)가 한 번 쓸 수 있다. 다른 회원이거나 시간이 지났으면 쓸 수 없다.
*   - 비밀번호가 있는 회원은 이 방식을 쓰지 않는다 (현재 비밀번호로 확인한다).
*   - 탈퇴 창을 닫을 때 cancel 로 세션에 남은 확인을 지운다 (이슈 #279, API 17-3).
*  */
@Slf4j
@Service
public class WithdrawVerificationService implements WithdrawVerifier {

    static final String SESSION_KEY = "withdrawVerification";
    /** 세션별 취소 횟수. 코드 요청이 시작된 뒤 취소가 있었으면 그 요청은 코드를 저장하지 않는다 (이슈 #279) */
    static final String CANCEL_GENERATION_KEY = "withdrawVerificationCancelGeneration";
    static final int CODE_LENGTH = 6;
    static final Duration CODE_TTL = Duration.ofMinutes(5);
    static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
    static final Duration VERIFIED_TTL = Duration.ofMinutes(5);
    static final int MAX_FAILURES = 5;
    private static final int COOLDOWN_PRUNE_SIZE = 1000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MemberService memberService;
    private final IdentityVerificationMailer mailer;
    private final Clock clock;
    /** 회원별로 마지막에 코드를 보낸 시각 (세션을 새로 만들어도 60초 제한을 지키려는 기록. 서버를 다시 켜면 지워진다) */
    private final Map<Long, Instant> lastSentAt = new ConcurrentHashMap<>();

    public WithdrawVerificationService(MemberService memberService, IdentityVerificationMailer mailer, Clock clock) {
        this.memberService = memberService;
        this.mailer = mailer;
        this.clock = clock;
    }

    @Override
    public void requestCode(Long memberId, HttpSession session) {
        // 요청이 시작된 시점의 취소 횟수를 기억해 두고, 코드를 저장하기 직전에 다시 비교한다 (그 사이 취소가 있었으면 이 요청은 버린다)
        long generation = cancelGenerationOf(session);
        String email = requireSocialMemberEmail(memberId);
        Instant now = clock.instant();
        // 이미 취소된 요청은 발송 간격(60초)을 차지하지 않도록, 간격을 예약하기 전에 먼저 취소 여부를 본다 (잠금 안에서 비교)
        synchronized (WebUtils.getSessionMutex(session)) {
            if (generation != cancelGenerationOf(session)) {
                throw new BusinessException(MemberErrorCode.IDENTITY_CODE_EXPIRED);
            }
        }
        if (!reserveSendSlot(memberId, now)) {
            throw new BusinessException(MemberErrorCode.IDENTITY_CODE_TOO_SOON);
        }
        String code = newCode();
        // 세션의 확인 상태를 바꾸는 요청(코드 요청·확인·사용)은 같은 세션이면 한 번에 하나씩 처리한다.
        // 이전 상태를 읽고 새 상태를 저장하고 발송이 거절되면 되돌리는 과정이 다른 요청에 끼어들리지 않게 한다 (메일은 비동기라 잠금은 짧다)
        synchronized (WebUtils.getSessionMutex(session)) {
            if (generation != cancelGenerationOf(session)) {
                // 이 요청이 진행되는 동안 탈퇴 창이 닫혀 취소됐다. 취소를 되돌리지 않도록 코드를 만들지도 보내지도 않고, 예약한 발송 간격도 돌려준다
                lastSentAt.remove(memberId, now);
                throw new BusinessException(MemberErrorCode.IDENTITY_CODE_EXPIRED);
            }
            Object previousState = session.getAttribute(SESSION_KEY);   // 발송이 거절되면 되돌린다
            session.setAttribute(SESSION_KEY, IdentityVerificationState.issued(email, IdentityVerificationService.hash(email, code), now.plus(CODE_TTL)));
            try {
                mailer.send(email, code);
            } catch (TaskRejectedException e) {
                // 발송 대기열이 가득 찼다. 보낸 적 없는 코드는 버리고 바로 다시 시도할 수 있게 하되, 이미 마친 확인이 있었다면 그대로 돌려놓는다
                if (previousState != null) {
                    session.setAttribute(SESSION_KEY, previousState);
                } else {
                    session.removeAttribute(SESSION_KEY);
                }
                lastSentAt.remove(memberId, now);
                log.warn("탈퇴 본인 확인 메일 대기열이 가득 차 요청을 건너뛰었어요.");
                throw new BusinessException(MemberErrorCode.IDENTITY_MAIL_UNAVAILABLE);
            }
        }
    }

    @Override
    public void verify(Long memberId, String code, HttpSession session) {
        String email = requireSocialMemberEmail(memberId);
        if (session == null) {
            throw new BusinessException(MemberErrorCode.IDENTITY_CODE_EXPIRED);   // 코드를 받은 적이 없다
        }
        // 같은 세션의 요청이 동시에 와도 한 번에 하나씩만 비교·횟수 증가·확인 완료를 한다 (시도 횟수 제한이 무의미해지지 않게 세션 잠금으로 묶는다)
        synchronized (WebUtils.getSessionMutex(session)) {
            IdentityVerificationState state = stateOf(session);
            if (state == null) {
                throw new BusinessException(MemberErrorCode.IDENTITY_CODE_EXPIRED);   // 코드를 받은 적이 없다
            }
            Instant now = clock.instant();
            if (!state.getEmail().equals(email) || !state.isCodeActive(now) || state.getFailures() >= MAX_FAILURES) {
                throw new BusinessException(MemberErrorCode.IDENTITY_CODE_EXPIRED);   // 다른 이메일의 코드, 만료, 이미 5번 틀림은 구분해 알리지 않는다
            }
            if (!IdentityVerificationService.matches(state, email, code)) {
                if (state.recordFailure() >= MAX_FAILURES) {
                    session.removeAttribute(SESSION_KEY);
                    throw new BusinessException(MemberErrorCode.IDENTITY_CODE_EXPIRED);
                }
                session.setAttribute(SESSION_KEY, state);   // 틀린 횟수를 세션에 다시 저장한다 (세션 저장소가 복사본을 쓰는 경우 대비)
                throw new BusinessException(MemberErrorCode.IDENTITY_CODE_INVALID);
            }
            state.completeVerification(now.plus(VERIFIED_TTL));
            session.setAttribute(SESSION_KEY, state);
        }
    }

    @Override
    public boolean consumeVerified(Long memberId, HttpSession session) {
        if (session == null) {
            return false;
        }
        String email = memberService.findLoginMemberById(memberId)
                .map(member -> SignupRules.normalizeEmail(member.getEmail()))
                .orElse(null);
        synchronized (WebUtils.getSessionMutex(session)) {
            // 상태를 읽는 것부터 지우는 것까지 세션 잠금 안에서 한다. 그래서 같은 세션의 동시 요청 중 성공은 정확히 한 번이고,
            // 그 사이에 코드 요청이 새 상태를 저장했다면 그 새 상태를 지우지 않는다
            IdentityVerificationState state = stateOf(session);
            // 확인을 마친 이메일이 이 회원의 이메일이고 아직 유효할 때만 한 번 쓴다
            if (state == null || email == null || !state.isVerifiedFor(email, clock.instant())) {
                return false;
            }
            session.removeAttribute(SESSION_KEY);
            return true;
        }
    }

    @Override
    public void cancel(Long memberId, HttpSession session) {
        if (session == null) {
            return;
        }
        // 코드 요청·확인·사용과 같은 세션 잠금 안에서 지운다. 그래서 늦게 도착한 코드 확인이 취소를 되돌리지 못한다
        // (확인은 저장된 상태가 있어야만 성공하고, 취소가 그 상태를 지웠으면 IDENTITY_CODE_EXPIRED 가 된다)
        // 상태는 이 회원의 것일 때만 지운다. 이 세션에 다른 회원의 상태가 있을 수 없지만(로그인 세션 하나에 회원 하나), 남의 것을 지우지 않는다
        String email = memberService.findLoginMemberById(memberId)
                .map(member -> SignupRules.normalizeEmail(member.getEmail()))
                .orElse(null);
        synchronized (WebUtils.getSessionMutex(session)) {
            IdentityVerificationState state = stateOf(session);
            // 취소 횟수를 올린다. 이미 시작했지만 아직 코드를 저장하지 않은 코드 요청은 이 값이 달라진 것을 보고 스스로 그만둔다
            session.setAttribute(CANCEL_GENERATION_KEY, cancelGenerationOf(session) + 1);
            if (state != null && (email == null || state.getEmail().equals(email))) {
                session.removeAttribute(SESSION_KEY);
            }
        }
    }

    /** 이 세션에서 지금까지 취소한 횟수 (한 번도 없으면 0). 세션 잠금 안에서 바꾸고, 읽을 때는 잠그지 않아도 값이 늘기만 해서 안전하다 */
    private static long cancelGenerationOf(HttpSession session) {
        Object value = session == null ? null : session.getAttribute(CANCEL_GENERATION_KEY);
        return value instanceof Long count ? count : 0L;
    }

    /** 비밀번호가 없는 간편 로그인 회원의 정규화한 가입 이메일. 회원이 없으면 MEMBER_NOT_FOUND, 비밀번호가 있는 회원이면 FORBIDDEN */
    private String requireSocialMemberEmail(Long memberId) {
        LoginMemberDTO member = memberService.findLoginMemberById(memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
        if (member.getPasswordHash() != null) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return SignupRules.normalizeEmail(member.getEmail());
    }

    /** 같은 회원이 60초 안에 보낸 적이 없으면 지금 보낸 것으로 기록하고 true */
    private boolean reserveSendSlot(Long memberId, Instant now) {
        if (lastSentAt.size() > COOLDOWN_PRUNE_SIZE) {
            lastSentAt.values().removeIf(sentAt -> !now.isBefore(sentAt.plus(RESEND_INTERVAL)));   // 오래된 기록을 지운다
        }
        boolean[] reserved = {false};
        lastSentAt.compute(memberId, (key, sentAt) -> {
            if (sentAt == null || !now.isBefore(sentAt.plus(RESEND_INTERVAL))) {
                reserved[0] = true;
                return now;
            }
            return sentAt;
        });
        return reserved[0];
    }

    private static IdentityVerificationState stateOf(HttpSession session) {
        Object attribute = session == null ? null : session.getAttribute(SESSION_KEY);
        return attribute instanceof IdentityVerificationState state ? state : null;
    }

    private static String newCode() {
        return String.format("%0" + CODE_LENGTH + "d", RANDOM.nextInt((int) Math.pow(10, CODE_LENGTH)));
    }
}
