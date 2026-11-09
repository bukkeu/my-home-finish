package com.wanted.moneybeetle.domain.operation.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.wanted.moneybeetle.domain.operation.model.dao.AdminLogRepository;
import com.wanted.moneybeetle.domain.operation.model.entity.AdminLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AdminLogServiceTest {

    private AdminLogRepository repository;
    private AdminLogService service;

    @BeforeEach
    void setUp() {
        repository = mock(AdminLogRepository.class);
        service = new AdminLogService(repository);
    }

    @Test
    @DisplayName("강의 허가 기록을 남긴다")
    void recordsCourseApprove() {
        service.record(1L, "COURSE_APPROVE", "COURSE", 33L, "{\"after\":\"APPROVED\"}");

        ArgumentCaptor<AdminLog> saved = ArgumentCaptor.forClass(AdminLog.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getAdminId()).isEqualTo(1L);
        assertThat(saved.getValue().getAction()).isEqualTo("COURSE_APPROVE");
        assertThat(saved.getValue().getTargetType()).isEqualTo("COURSE");
        assertThat(saved.getValue().getTargetId()).isEqualTo(33L);
        assertThat(saved.getValue().getDetail()).isEqualTo("{\"after\":\"APPROVED\"}");
    }

    @Test
    @DisplayName("detail 이 비어 있으면 null 로 저장한다")
    void blankDetailBecomesNull() {
        service.record(1L, "COURSE_REJECT", "COURSE", 33L, "  ");

        ArgumentCaptor<AdminLog> saved = ArgumentCaptor.forClass(AdminLog.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getDetail()).isNull();
    }

    @Test
    @DisplayName("JSON 이 아닌 detail 은 저장하지 않고 거절한다")
    void rejectsInvalidJson() {
        assertThatThrownBy(() -> service.record(1L, "COURSE_REJECT", "COURSE", 33L, "{not json"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("관리자·action·targetType 이 비었거나 너무 길면 거절한다")
    void rejectsInvalidFields() {
        assertThatThrownBy(() -> service.record(null, "A", "COURSE", 1L, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.record(1L, " ", "COURSE", 1L, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.record(1L, "A".repeat(51), "COURSE", 1L, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.record(1L, "A", "C".repeat(31), 1L, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }
}
