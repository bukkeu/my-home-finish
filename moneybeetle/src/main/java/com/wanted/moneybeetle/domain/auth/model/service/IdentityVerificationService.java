package com.wanted.moneybeetle.domain.auth.model.service;

import com.wanted.moneybeetle.domain.auth.model.dto.IdentityVerificationState;
import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.domain.member.model.policy.SignupRules;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

/* comment.
*   담당 : DBA ② (회원·인증)
*   가입 중 본인인증 - 이메일로 6자리 코드를 보내고 맞는지 확인한다 (USR-05, EP-02, 이슈 #9, API 5·5-1)
*   - 진행 상태(코드 해시·만료·틀린 횟수·인증 완료 표시)는 HTTP 세션에 담는다. DB 테이블은 만들지 않는다.
*     그래서 가입 화면을 연 브라우저(세션)에서 인증해야 그 세션의 가입(API 4)이 통과한다.
*   - 코드는 SecureRandom 6자리, 5분 안에 쓴다. 5번 틀리면 그 코드는 버리고 다시 받게 한다.
*   - 접속 주소(IP) 하나는 1분에 10번까지만 코드를 요청할 수 있다. 이메일을 바꿔 가며 여러 주소로 메일을 보내게 하는 것을 막는다.
*     주소는 요청의 접속 주소(getRemoteAddr)만 쓴다. 프록시 뒤에서는 모든 요청이 프록시 주소로 보이므로, 배포할 때 프록시를 믿는 설정과 함께 다시 정한다.
*   - 같은 이메일로는 60초에 한 번만 코드를 보낸다. 세션을 새로 만들어도 이 제한은 서버가 이메일 기준으로 지키므로
*     남의 이메일로 메일을 마구 보내는 일을 막는다. (서버를 다시 켜면 이 기록은 지워진다.)
*   - 이미 가입된 이메일은 코드를 보내지 않는다 (이메일 중복 확인 API 2 로 이미 알 수 있는 정보다).
*   - 인증이 끝난 표시는 30분(세션 만료와 같음) 동안 가입에 쓸 수 있고, 가입이 끝나면 지운다.
*   - 이 클래스는 트랜잭션을 열지 않는다 (DB 는 이메일 중복 조회뿐).
*  */
@Slf4j
@Service
public class IdentityVerificationService {

    static final String SESSION_KEY = "identityVerification";
    static final int CODE_LENGTH = 6;
    static final Duration CODE_TTL = Duration.ofMinutes(5);
    static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
    static final Duration VERIFIED_TTL = Duration.ofMinutes(30);
    static final int MAX_FAILURES = 5;
    /** 접속 주소 하나가 1분(IP_WINDOW)에 코드를 요청할 수 있는 횟수. 한 노트북에서 시연해도 넉넉한 값이다 */
    static final int IP_LIMIT = 10;
    static final Duration IP_WINDOW = Duration.ofMinutes(1);
    private static final int COOLDOWN_PRUNE_SIZE = 1000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MemberService memberService;
    private final IdentityVerificationMailer mailer;
    private final Clock clock;
    /** 이메일별로 마지막에 코드를 보낸 시각 (세션과 상관없이 60초 제한을 지키려는 기록) */
    private final Map<String, Instant> lastSentAt = new ConcurrentHashMap<>();
    /** 접속 주소별로 최근 IP_WINDOW 안에 요청한 시각들 (서버 메모리. 서버를 다시 켜면 지워진다) */
    private final Map<String, java.util.Deque<Instant>> requestsByIp = new ConcurrentHashMap<>();

    public IdentityVerificationService(MemberService memberService, IdentityVerificationMailer mailer, Clock clock) {
        this.memberService = memberService;
        this.mailer = mailer;
        this.clock = clock;
    }

    /**
     * 인증 코드를 메일로 보낸다 (API 5-1). 이전에 받은 코드는 쓸 수 없게 된다.
     *
     * @param clientIp 요청한 쪽의 주소. 컨트롤러가 요청의 접속 주소(getRemoteAddr)를 넘긴다. X-Forwarded-For 같은 헤더는 값을 마음대로 적을 수 있어 쓰지 않는다
     */
    public void requestCode(String rawEmail, String clientIp, HttpSession session) {
        Instant now = clock.instant();
        // 이메일을 바꿔 가며 보내는 요청(남의 주소 여러 곳으로 메일을 보내게 하는 것)을 막으려고, DB 를 보기 전에 접속 주소별 횟수부터 센다
        if (!reserveIpSlot(clientIp, now)) {
            throw new BusinessException(MemberErrorCode.IDENTITY_REQUEST_LIMITED);
        }
        String email = SignupRules.normalizeEmail(rawEmail);
        if (!SignupRules.isValidEmail(rawEmail) || !SignupRules.isValidEmail(email)) {
            throw new BusinessException(MemberErrorCode.EMAIL_INVALID);
        }
        if (!memberService.isEmailAvailable(rawEmail).isAvailable()) {
            throw new BusinessException(MemberErrorCode.EMAIL_DUPLICATED);
        }

        if (!reserveSendSlot(email, now)) {
            throw new BusinessException(MemberErrorCode.IDENTITY_CODE_TOO_SOON);
        }

        String code = newCode();
        session.setAttribute(SESSION_KEY, IdentityVerificationState.issued(email, hash(email, code), now.plus(CODE_TTL)));
        try {
            mailer.send(email, code);
        } catch (TaskRejectedException e) {
            // 발송 대기열이 가득 찼다. 보낸 적 없는 코드는 버리고 바로 다시 시도할 수 있게 한다. 이메일·코드는 로그에 남기지 않는다
            session.removeAttribute(SESSION_KEY);
            lastSentAt.remove(email);
            log.warn("본인인증 메일 대기열이 가득 차 요청을 건너뛰었어요.");
            throw new BusinessException(MemberErrorCode.IDENTITY_MAIL_UNAVAILABLE);
        }
    }

    /** 받은 코드가 맞으면 이 세션에서 그 이메일을 인증된 것으로 표시한다 (API 5) */
    public void verify(String rawEmail, String code, HttpSession session) {
        String email = SignupRules.normalizeEmail(rawEmail);
        IdentityVerificationState state = stateOf(session);
        if (state == null) {
            throw new BusinessException(MemberErrorCode.IDENTITY_CODE_EXPIRED);   // 코드를 받은 적이 없다
        }
        // 같은 세션의 요청이 동시에 와도 한 번에 하나씩만 비교·횟수 증가·인증 완료를 한다. 이렇게 묶지 않으면 여러 요청이 모두 "아직 5번 안
        // 틀렸다"고 보고 한꺼번에 코드를 대 볼 수 있어 시도 횟수 제한이 무의미해진다 (같은 세션이면 상태 객체가 하나라 이 객체로 잠근다)
        synchronized (state) {
            Instant now = clock.instant();
            if (!state.getEmail().equals(email) || !state.isCodeActive(now) || state.getFailures() >= MAX_FAILURES) {
                // 다른 이메일의 코드이거나, 5분이 지났거나, 이미 5번 틀렸다. 어느 쪽인지 구분해 알려 주지 않는다
                // (제한에 걸린 뒤에 늦게 들어온 요청은 코드를 비교하지 않는다)
                throw new BusinessException(MemberErrorCode.IDENTITY_CODE_EXPIRED);
            }
            if (!matches(state, email, code)) {
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

    /** 가입 전에 호출한다. 이 세션에서 그 이메일이 인증돼 있지 않으면 IDENTITY_NOT_VERIFIED 로 막는다 (API 4) */
    public void requireVerified(String rawEmail, HttpSession session) {
        IdentityVerificationState state = stateOf(session);
        if (state == null || !state.isVerifiedFor(SignupRules.normalizeEmail(rawEmail), clock.instant())) {
            throw new BusinessException(MemberErrorCode.IDENTITY_NOT_VERIFIED);
        }
    }

    /** 가입이 끝났다. 인증 표시를 지워 같은 인증으로 다른 이메일을 가입시키지 못하게 한다 */
    public void clear(HttpSession session) {
        session.removeAttribute(SESSION_KEY);
    }

    /** 이 접속 주소가 최근 IP_WINDOW 안에 IP_LIMIT 번 미만 요청했으면 이번 요청을 기록하고 true. 주소를 알 수 없으면 하나의 묶음("unknown")으로 센다 */
    private boolean reserveIpSlot(String clientIp, Instant now) {
        String key = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
        if (requestsByIp.size() > COOLDOWN_PRUNE_SIZE) {
            // 오래된 기록을 지운다. deque 는 스레드 안전하지 않으므로 값을 직접 훑지 않고, 키마다 computeIfPresent 로
            // 아래 compute 와 같은 잠금 안에서 읽고 지운다 (비었거나 마지막 기록이 창보다 오래된 키만 지우고, 나머지는 그대로 둔다)
            for (String ip : requestsByIp.keySet()) {
                requestsByIp.computeIfPresent(ip, (k, times) ->
                        times.isEmpty() || !now.isBefore(times.peekLast().plus(IP_WINDOW)) ? null : times);
            }
        }
        boolean[] reserved = {false};
        requestsByIp.compute(key, (ip, times) -> {
            java.util.Deque<Instant> recent = times == null ? new java.util.ArrayDeque<>() : times;
            while (!recent.isEmpty() && !now.isBefore(recent.peekFirst().plus(IP_WINDOW))) {
                recent.pollFirst();
            }
            if (recent.size() < IP_LIMIT) {
                recent.addLast(now);
                reserved[0] = true;
            }
            return recent;
        });
        return reserved[0];
    }

    /** 같은 이메일로 60초 안에 보낸 적이 없으면 지금 보낸 것으로 기록하고 true */
    private boolean reserveSendSlot(String email, Instant now) {
        if (lastSentAt.size() > COOLDOWN_PRUNE_SIZE) {
            lastSentAt.values().removeIf(sentAt -> !now.isBefore(sentAt.plus(RESEND_INTERVAL)));   // 오래된 기록을 지운다
        }
        boolean[] reserved = {false};
        lastSentAt.compute(email, (key, sentAt) -> {
            if (sentAt == null || !now.isBefore(sentAt.plus(RESEND_INTERVAL))) {
                reserved[0] = true;
                return now;
            }
            return sentAt;
        });
        return reserved[0];
    }

    private static IdentityVerificationState stateOf(HttpSession session) {
        Object attribute = session.getAttribute(SESSION_KEY);
        return attribute instanceof IdentityVerificationState state ? state : null;
    }

    private static boolean matches(IdentityVerificationState state, String email, String code) {
        if (code == null) {
            return false;
        }
        return MessageDigest.isEqual(
                state.getCodeHash().getBytes(StandardCharsets.UTF_8),
                hash(email, code).getBytes(StandardCharsets.UTF_8));
    }

    private static String newCode() {
        return String.format("%0" + CODE_LENGTH + "d", RANDOM.nextInt((int) Math.pow(10, CODE_LENGTH)));
    }

    /** 이메일과 코드를 함께 SHA-256 으로 해시한다 (세션에는 이 값만 담는다) */
    static String hash(String email, String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((email + ":" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 쓸 수 없어요.", e);
        }
    }
}
