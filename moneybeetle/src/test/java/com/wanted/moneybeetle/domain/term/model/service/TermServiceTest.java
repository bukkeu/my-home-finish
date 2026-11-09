package com.wanted.moneybeetle.domain.term.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.term.exception.TermErrorCode;
import com.wanted.moneybeetle.domain.term.model.dao.EconomicTermRepository;
import com.wanted.moneybeetle.domain.term.model.dao.TermScheduleRepository;
import com.wanted.moneybeetle.domain.term.model.entity.EconomicTerm;
import com.wanted.moneybeetle.domain.term.model.entity.TermSchedule;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import com.wanted.moneybeetle.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

class TermServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);

    private TermScheduleRepository scheduleRepository;
    private EconomicTermRepository termRepository;
    private TermAutoSelector autoSelector;
    private TermService service;

    @BeforeEach
    void setUp() {
        scheduleRepository = mock(TermScheduleRepository.class);
        termRepository = mock(EconomicTermRepository.class);
        autoSelector = mock(TermAutoSelector.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-09T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        service = new TermService(scheduleRepository, termRepository, autoSelector, clock);
        List<EconomicTerm> all = List.of(term(1), term(2), term(3), term(4), term(5), term(6));
        when(termRepository.findAllById(anyList())).thenAnswer(invocation -> {
            List<Long> ids = invocation.getArgument(0);
            return all.stream().filter(t -> ids.contains(t.getEconomicTermId())).toList();
        });
    }

    private EconomicTerm term(long id) {
        EconomicTerm term = EconomicTerm.create("용어" + id, "뜻" + id);
        ReflectionTestUtils.setField(term, "economicTermId", id);
        return term;
    }

    private List<TermSchedule> rows(LocalDate date, boolean auto, long... termIds) {
        java.util.ArrayList<TermSchedule> rows = new java.util.ArrayList<>();
        for (int i = 0; i < termIds.length; i++) {
            rows.add(TermSchedule.create(date, termIds[i], i + 1, auto));
        }
        return rows;
    }

    private void assertCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    @Test
    @DisplayName("오늘 일정이 있으면 그 5개를 순서대로 돌려주고 자동 선정은 하지 않는다")
    void todayFromSchedule() {
        when(scheduleRepository.findByShowDateOrderBySortOrderAsc(TODAY)).thenReturn(rows(TODAY, false, 3, 1, 5, 2, 4));

        var result = service.getToday();

        assertThat(result.date()).isEqualTo(TODAY);
        assertThat(result.terms()).extracting("economicTermId").containsExactly(3L, 1L, 5L, 2L, 4L);
        assertThat(result.terms().get(0).term()).isEqualTo("용어3");
        verify(autoSelector, never()).select(any());
    }

    @Test
    @DisplayName("오늘 일정이 없으면 자동 선정해서 돌려준다")
    void todayAutoSelected() {
        when(scheduleRepository.findByShowDateOrderBySortOrderAsc(TODAY)).thenReturn(List.of());
        when(autoSelector.select(TODAY)).thenReturn(rows(TODAY, true, 1, 2, 3, 4, 5));

        assertThat(service.getToday().terms()).extracting("economicTermId").containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    @DisplayName("동시에 다른 요청이 먼저 자동 선정을 저장했으면 저장된 값을 다시 읽어 돌려준다")
    void todayRaceFallsBackToStoredRows() {
        when(scheduleRepository.findByShowDateOrderBySortOrderAsc(TODAY))
                .thenReturn(List.of())
                .thenReturn(rows(TODAY, true, 2, 3, 4, 5, 6));
        when(autoSelector.select(TODAY)).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThat(service.getToday().terms()).extracting("economicTermId").containsExactly(2L, 3L, 4L, 5L, 6L);
    }

    @Test
    @DisplayName("등록된 용어가 없어서 선정할 것도 없으면 빈 목록이다")
    void todayEmptyWhenNoTerms() {
        when(scheduleRepository.findByShowDateOrderBySortOrderAsc(TODAY)).thenReturn(List.of());
        when(autoSelector.select(TODAY)).thenReturn(List.of());

        assertThat(service.getToday().terms()).isEmpty();
    }

    @Test
    @DisplayName("월별 현황: 일정이 있는 날만 날짜순으로, 자동 여부와 용어를 담는다")
    void findsMonth() {
        LocalDate d1 = LocalDate.of(2026, 11, 5);
        LocalDate d2 = LocalDate.of(2026, 11, 9);
        java.util.ArrayList<TermSchedule> all = new java.util.ArrayList<>(rows(d1, false, 1, 2, 3, 4, 5));
        all.addAll(rows(d2, true, 2, 3, 4, 5, 6));
        when(scheduleRepository.findByShowDateBetweenOrderByShowDateAscSortOrderAsc(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))).thenReturn(all);

        var result = service.findMonth(YearMonth.of(2026, 11));

        assertThat(result.days()).hasSize(2);
        assertThat(result.days().get(0).date()).isEqualTo(d1);
        assertThat(result.days().get(0).isAuto()).isFalse();
        assertThat(result.days().get(0).terms()).hasSize(5);
        assertThat(result.days().get(1).isAuto()).isTrue();
        assertThat(result.days().get(1).terms().get(0).term()).isEqualTo("용어2");
    }

    @Test
    @DisplayName("지정: 기존 일정을 지우고 순서 1~5 로 새로 저장한다 (오늘도 지정할 수 있다)")
    void assignsFiveTerms() {
        when(termRepository.findByEconomicTermIdInAndActiveTrue(anyCollection()))
                .thenReturn(List.of(term(1), term(2), term(3), term(4), term(5)));

        service.assign(TODAY, List.of(5L, 3L, 1L, 2L, 4L));

        verify(scheduleRepository).deleteByShowDate(TODAY);
        var saved = org.mockito.ArgumentCaptor.forClass(TermSchedule.class);
        verify(scheduleRepository, org.mockito.Mockito.times(5)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(TermSchedule::getEconomicTermId).containsExactly(5L, 3L, 1L, 2L, 4L);
        assertThat(saved.getAllValues()).extracting(TermSchedule::getSortOrder).containsExactly(1, 2, 3, 4, 5);
        assertThat(saved.getAllValues()).noneMatch(TermSchedule::isAuto);
    }

    @Test
    @DisplayName("지난 날짜는 PAST_DATE_LOCKED")
    void pastDateLocked() {
        assertCode(() -> service.assign(TODAY.minusDays(1), List.of(1L, 2L, 3L, 4L, 5L)), TermErrorCode.PAST_DATE_LOCKED);
        verify(scheduleRepository, never()).deleteByShowDate(any());
    }

    @Test
    @DisplayName("5개 미만은 TERMS_NOT_ENOUGH, 중복·5개 초과·null 은 TERM_SELECTION_INVALID")
    void invalidSelection() {
        assertCode(() -> service.assign(TODAY, List.of(1L, 2L, 3L, 4L)), TermErrorCode.TERMS_NOT_ENOUGH);
        assertCode(() -> service.assign(TODAY, List.of(1L, 1L, 2L, 3L)), TermErrorCode.TERMS_NOT_ENOUGH);
        assertCode(() -> service.assign(TODAY, List.of(1L, 1L, 2L, 3L, 4L, 5L)), TermErrorCode.TERM_SELECTION_INVALID);
        assertCode(() -> service.assign(TODAY, List.of(1L, 2L, 3L, 4L, 5L, 6L)), TermErrorCode.TERM_SELECTION_INVALID);
        assertCode(() -> service.assign(TODAY, java.util.Arrays.asList(1L, 2L, 3L, 4L, null)),
                TermErrorCode.TERM_SELECTION_INVALID);
        verify(scheduleRepository, never()).deleteByShowDate(any());
    }

    @Test
    @DisplayName("없거나 사용 중이 아닌 용어가 섞이면 TERM_NOT_FOUND")
    void unknownTerm() {
        when(termRepository.findByEconomicTermIdInAndActiveTrue(anyCollection()))
                .thenReturn(List.of(term(1), term(2), term(3), term(4)));

        assertCode(() -> service.assign(TODAY, List.of(1L, 2L, 3L, 4L, 99L)), TermErrorCode.TERM_NOT_FOUND);
        verify(scheduleRepository, never()).deleteByShowDate(any());
    }

    @Test
    @DisplayName("다른 관리자와 같은 날짜를 동시에 지정해 DB 제약에 걸리면 DUPLICATE_REQUEST")
    void concurrentAssign() {
        when(termRepository.findByEconomicTermIdInAndActiveTrue(anyCollection()))
                .thenReturn(List.of(term(1), term(2), term(3), term(4), term(5)));
        when(scheduleRepository.save(any(TermSchedule.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertCode(() -> service.assign(TODAY.plusDays(1), List.of(1L, 2L, 3L, 4L, 5L)), CommonErrorCode.DUPLICATE_REQUEST);
    }
}
