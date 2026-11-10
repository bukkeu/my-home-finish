package com.wanted.moneybeetle.global.log;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/* comment.
*   담당 : SCM (공통 기반·보안)
*   RequestLogFilter #84 확인
*   - 요청 ID 를 만들어 응답 헤더·MDC 에 넣고, 요청 안의 로그에서 같은 ID 를 쓴다
*   - 요청이 끝나면 "메서드 경로 상태 시간" 한 줄. 쿼리(?token=…)는 남기지 않는다
*   - 로그인한 회원 번호를 MDC 에 담는다. 요청이 끝나면 MDC 를 비운다
*  */
class RequestLogFilterTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new EchoController())
            .addFilters(new RequestLogFilter())
            .build();
    private final Logger filterLogger = (Logger) LoggerFactory.getLogger(RequestLogFilter.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @RestController
    static class EchoController {
        // 컨트롤러 안에서 보이는 요청 ID (같은 요청의 다른 로그에도 붙는지 확인용)
        @GetMapping("/api/v1/test/echo")
        public String echo() {
            return MDC.get(RequestLogFilter.MDC_REQUEST_ID);
        }

        @GetMapping("/css/app.css")
        public String css() {
            return "body{}";
        }
    }

    @BeforeEach
    void attachAppender() {
        logs.start();
        filterLogger.addAppender(logs);
    }

    @AfterEach
    void detachAppender() {
        filterLogger.detachAppender(logs);
        SecurityContextHolder.clearContext();
    }

    private List<ILoggingEvent> requestLines() {
        return logs.list;
    }

    @Test
    void 요청_ID_를_응답_헤더와_요청_안의_로그에_같이_쓴다() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/test/echo"))
                .andExpect(status().isOk())
                .andReturn();

        String requestId = result.getResponse().getHeader(RequestLogFilter.HEADER);
        assertThat(requestId).matches("[0-9a-f]{8}");
        assertThat(result.getResponse().getContentAsString()).isEqualTo(requestId);   // 컨트롤러에서 본 MDC
        assertThat(requestLines()).singleElement()
                .satisfies(e -> assertThat(e.getMDCPropertyMap()).containsEntry("requestId", requestId));
    }

    @Test
    void 요청_로그는_경로만_남기고_쿼리의_토큰은_남기지_않는다() throws Exception {
        mockMvc.perform(get("/api/v1/test/echo?token=secret-reset-token&password=pw1234"))
                .andExpect(status().isOk());

        assertThat(requestLines()).singleElement().satisfies(e -> {
            assertThat(e.getFormattedMessage()).startsWith("GET /api/v1/test/echo 200 ");
            assertThat(e.getFormattedMessage()).doesNotContain("secret-reset-token", "pw1234", "token=");
        });
    }

    @Test
    void 밖에서_보낸_요청_ID_는_형식이_맞을_때만_쓴다() throws Exception {
        mockMvc.perform(get("/api/v1/test/echo").header(RequestLogFilter.HEADER, "front-1234"))
                .andExpect(header().string(RequestLogFilter.HEADER, "front-1234"))
                .andExpect(content().string("front-1234"));

        // 줄바꿈·특수문자가 섞인 값은 버리고 새로 만든다 (로그 위조 방지)
        mockMvc.perform(get("/api/v1/test/echo").header(RequestLogFilter.HEADER, "abc\n[ERROR] fake"))
                .andExpect(header().string(RequestLogFilter.HEADER, org.hamcrest.Matchers.matchesPattern("[0-9a-f]{8}")));
    }

    @Test
    void 로그인한_회원_번호를_요청_로그에_담는다() throws Exception {
        AuthDetails details = mock(AuthDetails.class);
        when(details.getMemberId()).thenReturn(8L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, List.of()));

        mockMvc.perform(get("/api/v1/test/echo")).andExpect(status().isOk());

        assertThat(requestLines()).singleElement()
                .satisfies(e -> assertThat(e.getMDCPropertyMap()).containsEntry("memberId", "8"));
    }

    @Test
    void 정적_파일은_요청_로그를_남기지_않고_요청이_끝나면_MDC_를_비운다() throws Exception {
        mockMvc.perform(get("/css/app.css")).andExpect(status().isOk());

        assertThat(requestLines()).isEmpty();
        assertThat(MDC.get(RequestLogFilter.MDC_REQUEST_ID)).isNull();
        assertThat(MDC.get(RequestLogFilter.MDC_MEMBER_ID)).isNull();
    }

    @Test
    void 로그인한_요청에서_예외가_나도_요청_로그를_남기고_MDC_를_비운다() {
        AuthDetails details = mock(AuthDetails.class);
        when(details.getMemberId()).thenReturn(8L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, List.of()));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test/echo");
        request.addHeader(RequestLogFilter.HEADER, "front-1234");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // 필터 체인(컨트롤러·보안 필터)에서 예외가 난 경우
        assertThatThrownBy(() -> new RequestLogFilter().doFilter(request, response, (req, res) -> {
            assertThat(MDC.get(RequestLogFilter.MDC_REQUEST_ID)).isEqualTo("front-1234");   // 예외 전에는 들어 있다
            throw new IOException("연결 끊김");
        })).isInstanceOf(IOException.class);

        assertThat(requestLines()).singleElement().satisfies(e -> assertThat(e.getMDCPropertyMap())
                .containsEntry("requestId", "front-1234").containsEntry("memberId", "8"));
        assertThat(MDC.get(RequestLogFilter.MDC_REQUEST_ID)).isNull();                        // 예외 뒤에는 비워진다
        assertThat(MDC.get(RequestLogFilter.MDC_MEMBER_ID)).isNull();
    }
}
