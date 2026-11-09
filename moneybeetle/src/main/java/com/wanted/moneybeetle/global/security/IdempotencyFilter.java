package com.wanted.moneybeetle.global.security;

/* comment.
*   담당 : PM (중복 요청 방지, #65), 검토 : SCM
*   Idempotency-Key 헤더로 같은 요청을 한 번만 처리 (DP-37, API 명세서 1-1)
*   대상 : 퀴즈 제출, 매수·매도, 청약통장 납입, 강의 영상·요약글 저장(API 78)
*   - 키는 "로그인 회원 + 메서드 + 주소 + Idempotency-Key" 로 구분한다
*   - 같은 키 + 같은 내용이 다시 오면 처음 결과(상태 코드·본문)를 그대로 돌려준다
*   - 같은 키 + 다른 내용이면 처리하지 않고 409 IDEMPOTENCY_KEY_CONFLICT
*   - 처음 요청이 아직 처리 중이면 409 DUPLICATE_REQUEST
*   - 결과는 24시간 보관한다. 단 4xx(요청이 거절됨)는 기록을 지워서 같은 키로 다시 시도할 수 있다
*     5xx는 처리됐는지 알 수 없어서(커밋 뒤 응답 중 오류 등) 그대로 보관한다 -> 같은 키로 다시 실행되지 않는다
*     응답을 만들지 못하고 필터 밖으로 예외·Error 가 나간 경우도 같다: 처리됐는지 알 수 없으므로 500 INTERNAL_ERROR 결과로
*     보관하고 예외는 그대로 다시 던진다. 지우면 커밋 뒤 예외에서 같은 키 재시도가 다시 실행되고, 그대로 두면 "처리 중"이
*     24시간 남아 같은 키가 계속 DUPLICATE_REQUEST 가 되기 때문이다
*   - 헤더가 없으면 막지 않고 그대로 처리한다 (화면은 항상 헤더를 보낸다)
*   - 키가 100자를 넘으면 처리하지 않고 400 IDEMPOTENCY_KEY_TOO_LONG (아주 긴 키로 메모리를 쓰는 것 방지)
*   Spring Security 필터 다음에 실행되므로 로그인 정보(getUserPrincipal)를 쓸 수 있다
*  */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanted.moneybeetle.global.common.ApiResponse;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import com.wanted.moneybeetle.global.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

@Component
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";
    /** 키 길이 제한 : 아주 긴 키를 계속 보내 메모리를 쓰는 것을 막는다 (UUID 는 36자) */
    static final int MAX_KEY_LENGTH = 100;

    /** 중복 요청 방지를 적용하는 API (메서드, 주소 패턴) */
    private static final List<Target> TARGETS = List.of(
            new Target("POST", "/api/v1/courses/*/quiz/attempts"),      // 퀴즈 제출 (API 42)
            new Target("POST", "/api/v1/trading/orders"),               // 매수·매도 (API 60)
            new Target("POST", "/api/v1/housing/account/deposits"),     // 청약통장 납입 (API 66)
            new Target("PUT", "/api/v1/instructor/courses/*/lessons")   // 강의 영상·요약글 저장 (API 78)
    );

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final ObjectMapper objectMapper;
    private final IdempotencyStore store;

    /** 시계는 팀 공통 Clock(TimeConfig, Asia/Seoul)을 주입받는다 (테스트에서 Clock.fixed 로 바꿀 수 있다) */
    @Autowired
    public IdempotencyFilter(ObjectMapper objectMapper, Clock clock) {
        this(objectMapper, new InMemoryIdempotencyStore(clock));
    }

    IdempotencyFilter(ObjectMapper objectMapper, IdempotencyStore store) {
        this.objectMapper = objectMapper;
        this.store = store;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return TARGETS.stream().noneMatch(target -> target.matches(request.getMethod(), path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String idempotencyKey = request.getHeader(HEADER);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            chain.doFilter(request, response);
            return;
        }
        if (idempotencyKey.length() > MAX_KEY_LENGTH) {
            writeError(response, CommonErrorCode.IDEMPOTENCY_KEY_TOO_LONG);   // 처리하지 않고 400
            return;
        }

        CachedBodyRequest cachedRequest = new CachedBodyRequest(request);
        String storeKey = storeKey(request, idempotencyKey);
        String bodyHash = sha256(cachedRequest.getBody());

        if (!store.start(storeKey, bodyHash)) {
            handleRepeated(storeKey, bodyHash, response);
            return;
        }

        ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);
        boolean handled = false;
        try {
            chain.doFilter(cachedRequest, cachedResponse);
            handled = true;
        } finally {
            // 보통 예외는 GlobalExceptionHandler 가 500 응답으로 바꿔서 여기까지 오지 않는다.
            // 여기 오는 것은 응답을 만들지 못한 경우(Error 포함)다. 처리가 끝났는지(커밋됐는지) 알 수 없으므로
            // 5xx 와 같게 500 결과로 보관해, 같은 키로 다시 와도 한 번 더 실행되지 않게 한다
            if (!handled) {
                storeUnknownFailure(storeKey);
            }
        }

        int status = cachedResponse.getStatus();
        boolean clientError = status >= 400 && status < 500;
        if (clientError) {
            store.remove(storeKey);
        } else {
            store.complete(storeKey, status, cachedResponse.getContentType(),
                    cachedResponse.getContentAsByteArray());
        }
        cachedResponse.copyBodyToResponse();
    }

    /** 응답 없이 끝난 요청을 500 INTERNAL_ERROR 결과로 보관한다. 여기서 실패해도 원래 예외를 가리지 않는다 */
    private void storeUnknownFailure(String storeKey) {
        try {
            store.complete(storeKey, CommonErrorCode.INTERNAL_ERROR.getStatus().value(),
                    MediaType.APPLICATION_JSON_VALUE,
                    objectMapper.writeValueAsBytes(ApiResponse.fail(CommonErrorCode.INTERNAL_ERROR)));
        } catch (RuntimeException | IOException e) {
            // 보관하지 못하면 기록을 지운다(처리 중으로 남기지 않는다)
            store.remove(storeKey);
        }
    }

    /** 같은 키가 이미 있을 때: 내용이 다르면 거절, 처리 중이면 거절, 끝났으면 처음 결과를 다시 보낸다 */
    private void handleRepeated(String storeKey, String bodyHash, HttpServletResponse response)
            throws IOException {
        Optional<IdempotencyRecord> found = store.find(storeKey);
        if (found.isEmpty()) {
            // 확인하는 사이에 처음 요청이 실패해 기록이 지워졌다 -> 다시 시도하도록 안내
            writeError(response, CommonErrorCode.DUPLICATE_REQUEST);
            return;
        }
        IdempotencyRecord record = found.get();
        if (!record.isSameBody(bodyHash)) {
            writeError(response, CommonErrorCode.IDEMPOTENCY_KEY_CONFLICT);
            return;
        }
        if (record.isInProgress()) {
            writeError(response, CommonErrorCode.DUPLICATE_REQUEST);
            return;
        }
        response.setStatus(record.status());
        if (record.contentType() != null) {
            response.setContentType(record.contentType());
        }
        response.setHeader(REPLAYED_HEADER, "true");
        response.getOutputStream().write(record.body());
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.fail(errorCode));
    }

    private String storeKey(HttpServletRequest request, String idempotencyKey) {
        Principal principal = request.getUserPrincipal();
        String owner = principal == null ? "anonymous" : principal.getName();
        return owner + " " + request.getMethod() + " " + request.getRequestURI() + " " + idempotencyKey;
    }

    private static String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 쓸 수 없습니다", e);
        }
    }

    private record Target(String method, String pattern) {
        boolean matches(String requestMethod, String path) {
            return method.equals(requestMethod) && PATH_MATCHER.match(pattern, path);
        }
    }
}
