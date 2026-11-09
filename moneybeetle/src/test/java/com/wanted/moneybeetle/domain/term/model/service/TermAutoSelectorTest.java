package com.wanted.moneybeetle.domain.term.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.term.model.dao.EconomicTermRepository;
import com.wanted.moneybeetle.domain.term.model.dao.TermScheduleRepository;
import com.wanted.moneybeetle.domain.term.model.entity.EconomicTerm;
import com.wanted.moneybeetle.domain.term.model.entity.TermSchedule;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TermAutoSelectorTest {

    private EconomicTermRepository termRepository;
    private TermScheduleRepository scheduleRepository;
    private TermAutoSelector selector;

    @BeforeEach
    void setUp() {
        termRepository = mock(EconomicTermRepository.class);
        scheduleRepository = mock(TermScheduleRepository.class);
        selector = new TermAutoSelector(termRepository, scheduleRepository);
        when(scheduleRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void givenActiveTerms(int count) {
        List<EconomicTerm> terms = new ArrayList<>();
        for (long id = 1; id <= count; id++) {
            EconomicTerm term = EconomicTerm.create("용어" + id, "뜻" + id);
            ReflectionTestUtils.setField(term, "economicTermId", id);
            terms.add(term);
        }
        when(termRepository.findByActiveTrueOrderByEconomicTermIdAsc()).thenReturn(terms);
    }

    private List<Long> termIds(List<TermSchedule> rows) {
        return rows.stream().map(TermSchedule::getEconomicTermId).toList();
    }

    @Test
    @DisplayName("사용 중인 용어를 등록 순서로 5개 고르고, 순서 1~5 와 자동 선정 표시를 붙인다")
    void picksFiveInRegistrationOrder() {
        givenActiveTerms(12);
        LocalDate date = LocalDate.ofEpochDay(0);   // 시작 위치 0

        List<TermSchedule> rows = selector.select(date);

        assertThat(termIds(rows)).containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(rows).extracting(TermSchedule::getSortOrder).containsExactly(1, 2, 3, 4, 5);
        assertThat(rows).allMatch(TermSchedule::isAuto);
        assertThat(rows).allMatch(row -> row.getShowDate().equals(date));
    }

    @Test
    @DisplayName("다음 날은 이어서 다음 5개를 고르고, 끝에 닿으면 처음으로 돌아간다")
    void continuesAndWrapsAround() {
        givenActiveTerms(12);

        assertThat(termIds(selector.select(LocalDate.ofEpochDay(1)))).containsExactly(6L, 7L, 8L, 9L, 10L);
        assertThat(termIds(selector.select(LocalDate.ofEpochDay(2)))).containsExactly(11L, 12L, 1L, 2L, 3L);
    }

    @Test
    @DisplayName("같은 날짜는 언제 불러도 같은 5개다")
    void sameDateSameTerms() {
        givenActiveTerms(9);
        LocalDate date = LocalDate.of(2026, 11, 9);

        assertThat(termIds(selector.select(date))).isEqualTo(termIds(selector.select(date)));
    }

    @Test
    @DisplayName("용어가 5개보다 적으면 있는 만큼만 고르고, 하나도 없으면 저장하지 않는다")
    void fewerThanFiveOrNone() {
        givenActiveTerms(3);
        assertThat(termIds(selector.select(LocalDate.of(2026, 11, 9)))).hasSize(3).doesNotHaveDuplicates();

        givenActiveTerms(0);
        assertThat(selector.select(LocalDate.of(2026, 11, 10))).isEmpty();
        verify(scheduleRepository, org.mockito.Mockito.times(1)).saveAll(anyList());
        verify(scheduleRepository, never()).deleteByShowDate(org.mockito.ArgumentMatchers.any());
    }
}
