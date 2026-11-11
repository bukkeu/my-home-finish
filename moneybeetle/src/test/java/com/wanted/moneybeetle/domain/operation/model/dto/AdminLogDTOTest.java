package com.wanted.moneybeetle.domain.operation.model.dto;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.wanted.moneybeetle.domain.operation.model.entity.AdminLog;
import com.wanted.moneybeetle.support.LogCapture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : DBA ② (관리자 운영)
*   AdminLogDTO (이슈 #299) - detail 을 JSON 으로 읽지 못해도 응답은 null 로 두고, 원인을 warn 로그로 남기는지 확인한다
*  */
class AdminLogDTOTest {

    private static AdminLog log(long id, String detail) {
        AdminLog log = AdminLog.create(1L, "SETTING_UPDATE", "SYSTEM_SETTING", 4L, detail);
        ReflectionTestUtils.setField(log, "adminLogId", id);
        return log;
    }

    @Test
    @DisplayName("정상 JSON 이면 객체로 읽고 로그를 남기지 않는다")
    void validJson() {
        try (LogCapture logs = LogCapture.of(AdminLogDTO.class)) {
            AdminLogDTO dto = AdminLogDTO.of(log(77, "{\"from\":4,\"to\":5}"), "김운영");

            assertThat(dto.detail().get("to").asInt()).isEqualTo(5);
            assertThat(logs.events()).isEmpty();
        }
    }

    @Test
    @DisplayName("detail 이 비어 있으면(null) 로그 없이 null 이다")
    void nullDetail() {
        try (LogCapture logs = LogCapture.of(AdminLogDTO.class)) {
            AdminLogDTO dto = AdminLogDTO.of(log(77, null), "김운영");

            assertThat(dto.detail()).isNull();
            assertThat(logs.events()).isEmpty();
        }
    }

    @Test
    @DisplayName("JSON 을 읽지 못하면 detail 은 null 이고, 기록 번호와 예외를 warn 로그로 남긴다 (스택 포함)")
    void invalidJsonLogsCause() {
        try (LogCapture logs = LogCapture.of(AdminLogDTO.class)) {
            AdminLogDTO dto = AdminLogDTO.of(log(77, "{\"secretKey\":\"top-secret-value\""), "김운영");

            assertThat(dto.detail()).isNull();
            assertThat(dto.adminLogId()).isEqualTo(77L);
            assertThat(logs.events()).hasSize(1);
            ILoggingEvent event = logs.events().get(0);
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).isEqualTo("관리자 로그 상세 변환 실패 adminLogId=77");
            assertThat(event.getThrowableProxy()).isNotNull();
            assertThat(event.getThrowableProxy().getStackTraceElementProxyArray()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("로그의 메시지·예외 메시지에 detail 원문이 실리지 않는다")
    void doesNotLogDetailContent() {
        try (LogCapture logs = LogCapture.of(AdminLogDTO.class)) {
            AdminLogDTO.of(log(78, "{\"secretKey\":\"top-secret-value\""), "김운영");

            ILoggingEvent event = logs.events().get(0);
            assertThat(event.getFormattedMessage()).doesNotContain("secretKey", "top-secret-value");
            assertThat(event.getThrowableProxy().getMessage()).doesNotContain("secretKey", "top-secret-value");
        }
    }
}
