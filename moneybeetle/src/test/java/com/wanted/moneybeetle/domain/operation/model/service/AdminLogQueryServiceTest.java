package com.wanted.moneybeetle.domain.operation.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.operation.model.dao.AdminLogRepository;
import com.wanted.moneybeetle.domain.operation.model.entity.AdminLog;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class AdminLogQueryServiceTest {

    private AdminLogRepository repository;
    private MemberService memberService;
    private AdminLogQueryService service;

    @BeforeEach
    void setUp() {
        repository = mock(AdminLogRepository.class);
        memberService = mock(MemberService.class);
        service = new AdminLogQueryService(repository, memberService);
    }

    private AdminLog log(long id, long adminId, String detail) {
        AdminLog log = AdminLog.create(adminId, "TURN_ADVANCE", "SEASON", 7L, detail);
        ReflectionTestUtils.setField(log, "adminLogId", id);
        ReflectionTestUtils.setField(log, "createdAt", LocalDateTime.of(2026, 11, 5, 0, 0, 3));
        return log;
    }

    @Test
    @DisplayName("관리자 닉네임을 붙이고 detail 은 JSON 객체로 돌려준다")
    void attachesNicknameAndParsesDetail() {
        Slice<AdminLog> page = new SliceImpl<>(List.of(log(77, 1, "{\"from\":4,\"to\":5}"), log(76, 2, null)));
        when(repository.search(any(), any(), any(), any(), any(), any(Pageable.class))).thenReturn(page);
        when(memberService.findNicknames(any())).thenReturn(Map.of(1L, "김운영"));

        var result = service.findLogs("SEASON", 7L, null, null, null, null, null);

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).adminNickname()).isEqualTo("김운영");
        assertThat(result.items().get(0).detail().get("to").asInt()).isEqualTo(5);
        assertThat(result.items().get(1).adminNickname()).isNull();
        assertThat(result.items().get(1).detail()).isNull();
    }

    @Test
    @DisplayName("기간은 from 날짜 시작부터 to 날짜 끝까지(다음 날 0시 미만)로 바꾸고, 쪽 크기는 기본 50·최대 100 이다")
    void convertsPeriodAndPaging() {
        when(repository.search(any(), any(), any(), any(), any(), any(Pageable.class))).thenReturn(new SliceImpl<>(List.of()));

        service.findLogs(" ", null, 1L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5), -3, 500);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).search(eq(null), eq(null), eq(1L), eq(LocalDateTime.of(2026, 11, 1, 0, 0)),
                eq(LocalDateTime.of(2026, 11, 6, 0, 0)), pageable.capture());
        assertThat(pageable.getValue()).isEqualTo(PageRequest.of(0, 100));

        service.findLogs(null, null, null, null, null, null, null);
        verify(repository).search(eq(null), eq(null), eq(null), eq(null), eq(null), eq(PageRequest.of(0, 50)));
    }
}
