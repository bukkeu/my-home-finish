package com.wanted.moneybeetle.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

/* comment.
*   담당 : PM 이재성 (#65)
*   IdempotencyFilter 확인 (스프링 없이 단독 실행)
*   - 같은 키·같은 내용은 한 번만 처리하고 처음 결과를 다시 돌려준다
*   - 같은 키·다른 내용은 409 IDEMPOTENCY_KEY_CONFLICT, 처리 중이면 409 DUPLICATE_REQUEST
*   - 4xx 응답은 저장하지 않고, 5xx 응답은 저장한다. 24시간이 지나면 기록이 지워진다
*  */
class IdempotencyFilterTest {

    private static final String ORDER_PATH = "/api/v1/trading/orders";
    private static final String BUY_BODY = "{\"stockId\":1,\"orderType\":\"BUY\",\"quantity\":6}";
    private static final String CREATED_BODY = "{\"success\":true,\"data\":{\"orderId\":901},\"error\":null}";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-09T00:00:00Z"));
    private final InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(clock);
    private final IdempotencyFilter filter = new IdempotencyFilter(new ObjectMapper(), store);

    /** Controller 대신 호출 횟수를 세고 정해진 응답을 쓰는 체인 */
    private static FilterChain chain(AtomicInteger calls, int status, String body) {
        return (req, res) -> {
            calls.incrementAndGet();
            req.getInputStream().readAllBytes();   // Controller 처럼 본문을 읽는다
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ((HttpServletResponse) res).setStatus(status);
            res.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        };
    }

    private MockHttpServletRequest request(String member, String key, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", ORDER_PATH);
        request.setUserPrincipal(new UsernamePasswordAuthenticationToken(member, null));
        if (key != null) {
            request.addHeader(IdempotencyFilter.HEADER, key);
        }
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private MockHttpServletResponse send(MockHttpServletRequest request, FilterChain chain)
            throws IOException, ServletException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void 대상이_아닌_API는_검사하지_않는다() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", ORDER_PATH);
        request.addHeader(IdempotencyFilter.HEADER, "k-1");

        send(request, chain(calls, 200, "{}"));
        send(request, chain(calls, 200, "{}"));

        assertThat(calls).hasValue(2);
        assertThat(store.size()).isZero();
    }

    @Test
    void 헤더가_없으면_막지_않고_그대로_처리한다() throws Exception {
        AtomicInteger calls = new AtomicInteger();

        send(request("minji", null, BUY_BODY), chain(calls, 201, CREATED_BODY));
        send(request("minji", null, BUY_BODY), chain(calls, 201, CREATED_BODY));

        assertThat(calls).hasValue(2);
    }

    @Test
    void 같은_키와_같은_내용은_한_번만_처리하고_처음_결과를_그대로_돌려준다() throws Exception {
        AtomicInteger calls = new AtomicInteger();

        MockHttpServletResponse first = send(request("minji", "k-1", BUY_BODY), chain(calls, 201, CREATED_BODY));
        MockHttpServletResponse second = send(request("minji", "k-1", BUY_BODY), chain(calls, 201, "{\"other\":1}"));

        assertThat(calls).hasValue(1);
        assertThat(first.getStatus()).isEqualTo(201);
        assertThat(first.getContentAsString()).isEqualTo(CREATED_BODY);
        assertThat(second.getStatus()).isEqualTo(201);
        assertThat(second.getContentAsString()).isEqualTo(CREATED_BODY);
        assertThat(second.getHeader(IdempotencyFilter.REPLAYED_HEADER)).isEqualTo("true");
    }

    @Test
    void 같은_키로_내용이_다르면_409_IDEMPOTENCY_KEY_CONFLICT() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        send(request("minji", "k-1", BUY_BODY), chain(calls, 201, CREATED_BODY));

        MockHttpServletResponse response = send(
                request("minji", "k-1", BUY_BODY.replace("6", "7")), chain(calls, 201, CREATED_BODY));

        assertThat(calls).hasValue(1);
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .contains("\"success\":false")
                .contains("IDEMPOTENCY_KEY_CONFLICT");
    }

    @Test
    void 처음_요청이_처리_중이면_409_DUPLICATE_REQUEST() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        // 처음 요청을 처리하는 도중에 같은 요청이 한 번 더 들어온 상황
        FilterChain slowChain = (req, res) -> {
            calls.incrementAndGet();
            MockHttpServletResponse inner = send(request("minji", "k-1", BUY_BODY), chain(calls, 201, CREATED_BODY));
            assertThat(inner.getStatus()).isEqualTo(409);
            assertThat(inner.getContentAsString(StandardCharsets.UTF_8)).contains("DUPLICATE_REQUEST");
            ((HttpServletResponse) res).setStatus(201);
        };

        send(request("minji", "k-1", BUY_BODY), slowChain);

        assertThat(calls).hasValue(1);
    }

    @Test
    void 서버_오류_응답은_저장해서_같은_키로_다시_실행하지_않는다() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        String errorBody = "{\"success\":false,\"error\":{\"code\":\"INTERNAL_ERROR\"}}";

        MockHttpServletResponse failed = send(request("minji", "k-1", BUY_BODY), chain(calls, 500, errorBody));
        MockHttpServletResponse retried = send(request("minji", "k-1", BUY_BODY), chain(calls, 201, CREATED_BODY));

        assertThat(calls).hasValue(1);
        assertThat(failed.getStatus()).isEqualTo(500);
        assertThat(retried.getStatus()).isEqualTo(500);
        assertThat(retried.getContentAsString()).isEqualTo(errorBody);
        assertThat(retried.getHeader(IdempotencyFilter.REPLAYED_HEADER)).isEqualTo("true");
    }

    @Test
    void 거절된_4xx_응답은_저장하지_않아_같은_키로_다시_시도할_수_있다() throws Exception {
        AtomicInteger calls = new AtomicInteger();

        MockHttpServletResponse failed = send(request("minji", "k-1", BUY_BODY),
                chain(calls, 400, "{\"success\":false}"));
        MockHttpServletResponse retried = send(request("minji", "k-1", BUY_BODY), chain(calls, 201, CREATED_BODY));

        assertThat(calls).hasValue(2);
        assertThat(failed.getStatus()).isEqualTo(400);
        assertThat(retried.getStatus()).isEqualTo(201);
    }

    @Test
    void 처리_중_예외가_나면_기록을_지운다() throws Exception {
        FilterChain broken = (req, res) -> {
            throw new IllegalStateException("처리 실패");
        };

        try {
            send(request("minji", "k-1", BUY_BODY), broken);
        } catch (IllegalStateException expected) {
            // 예외는 그대로 위로 전달된다
        }

        assertThat(store.find("minji POST " + ORDER_PATH + " k-1")).isEmpty();
    }

    @Test
    void 회원이_다르면_같은_키라도_따로_처리한다() throws Exception {
        AtomicInteger calls = new AtomicInteger();

        send(request("minji", "k-1", BUY_BODY), chain(calls, 201, CREATED_BODY));
        send(request("bokri", "k-1", BUY_BODY), chain(calls, 201, CREATED_BODY));

        assertThat(calls).hasValue(2);
    }

    @Test
    void Controller가_본문을_다시_읽을_수_있다() throws Exception {
        StringBuilder readBody = new StringBuilder();
        FilterChain reading = (req, res) -> {
            readBody.append(new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            ((HttpServletResponse) res).setStatus(201);
        };

        send(request("minji", "k-1", BUY_BODY), reading);

        assertThat(readBody.toString()).isEqualTo(BUY_BODY);
    }

    @Test
    void 기록은_24시간이_지나면_지워져_다시_처리된다() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        send(request("minji", "k-1", BUY_BODY), chain(calls, 201, CREATED_BODY));

        clock.advance(Duration.ofHours(23));
        send(request("minji", "k-1", BUY_BODY), chain(calls, 201, CREATED_BODY));
        assertThat(calls).hasValue(1);

        clock.advance(Duration.ofHours(1));
        send(request("minji", "k-1", BUY_BODY), chain(calls, 201, CREATED_BODY));
        assertThat(calls).hasValue(2);
    }

    @Test
    void 퀴즈_제출_청약_납입_강의_저장도_대상이다() {
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("POST", "/api/v1/courses/4/quiz/attempts"))).isFalse();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("POST", "/api/v1/housing/account/deposits"))).isFalse();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("PUT", "/api/v1/instructor/courses/4/lessons"))).isFalse();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/v1/trading/orders"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("POST", "/api/v1/courses/4/scraps"))).isTrue();
    }

    /** 테스트에서 시간을 앞으로 돌릴 수 있는 시계 */
    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
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
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
