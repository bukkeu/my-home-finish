package com.wanted.moneybeetle.global.log;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/* comment.
*   담당 : SCM (공통 기반·보안)
*   #84 실제 application.yaml 의 로그 설정으로 콘솔에 찍힌 글자를 확인한다 (MDC 값이 아니라 출력 문자열)
*   - 콘솔 한 줄마다 [요청 ID|m=회원 ID] 가 붙는다. 비로그인 요청은 회원 ID 칸이 빈다
*   DB·웹 서버 없이 설정만 읽어 로그 시스템을 띄운다 (기본 프로필 = CI 와 같은 조건)
*  */
@ExtendWith(OutputCaptureExtension.class)
class LogPatternTest {

    @Configuration
    static class NoBeans {
    }

    @Test
    void 콘솔_로그_줄에_요청_ID_와_회원_ID_가_찍힌다(CapturedOutput output) {
        try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(NoBeans.class)
                .web(WebApplicationType.NONE).run()) {
            var log = LoggerFactory.getLogger(RequestLogFilter.class);
            try {
                MDC.put(RequestLogFilter.MDC_REQUEST_ID, "a1b2c3d4");
                MDC.put(RequestLogFilter.MDC_MEMBER_ID, "8");
                log.info("GET /api/v1/housing/complexes 200 12ms");

                MDC.remove(RequestLogFilter.MDC_MEMBER_ID);
                log.info("GET /api/v1/terms/today 200 3ms");
            } finally {
                MDC.clear();
            }
        }

        assertThat(output.getOut()).containsSubsequence(
                "[a1b2c3d4|m=8] ", "GET /api/v1/housing/complexes 200 12ms",
                "[a1b2c3d4|m=] ", "GET /api/v1/terms/today 200 3ms");
    }
}
