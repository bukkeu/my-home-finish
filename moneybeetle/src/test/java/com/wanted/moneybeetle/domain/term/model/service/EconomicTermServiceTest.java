package com.wanted.moneybeetle.domain.term.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.term.exception.TermErrorCode;
import com.wanted.moneybeetle.domain.term.model.dao.EconomicTermRepository;
import com.wanted.moneybeetle.domain.term.model.dao.TermScheduleRepository;
import com.wanted.moneybeetle.domain.term.model.dto.TermCreateDTO;
import com.wanted.moneybeetle.domain.term.model.dto.TermUpdateDTO;
import com.wanted.moneybeetle.domain.term.model.entity.EconomicTerm;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;

class EconomicTermServiceTest {

    private EconomicTermRepository termRepository;
    private TermScheduleRepository scheduleRepository;
    private EconomicTermService service;
    private EconomicTerm term;

    @BeforeEach
    void setUp() {
        termRepository = mock(EconomicTermRepository.class);
        scheduleRepository = mock(TermScheduleRepository.class);
        service = new EconomicTermService(termRepository, scheduleRepository);
        term = EconomicTerm.create("Compound", "복리");
        ReflectionTestUtils.setField(term, "economicTermId", 3L);
        when(termRepository.findById(3L)).thenReturn(Optional.of(term));
    }

    private void assertCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    @Test
    @DisplayName("목록: 키워드를 다듬어 넘기고, 쪽 크기는 기본 50·최대 100 이다")
    void listsWithPaging() {
        when(termRepository.search(any(), any(Pageable.class))).thenReturn(new SliceImpl<>(List.of(term)));

        var result = service.findTerms("  복리 ", null, null);
        service.findTerms(" ", -1, 999);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).isActive()).isTrue();
        verify(termRepository).search("복리", PageRequest.of(0, 50));
        verify(termRepository).search(null, PageRequest.of(0, 100));
    }

    @Test
    @DisplayName("등록: 앞뒤 공백을 지우고 저장하고 새 번호를 돌려준다")
    void creates() {
        when(termRepository.existsByTerm("단리")).thenReturn(false);
        when(termRepository.saveAndFlush(any(EconomicTerm.class))).thenAnswer(invocation -> {
            EconomicTerm saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "economicTermId", 11L);
            return saved;
        });

        var result = service.create(new TermCreateDTO(" 단리 ", "원금에만 이자가 붙는 방식"));

        assertThat(result.economicTermId()).isEqualTo(11L);
        ArgumentCaptor<EconomicTerm> saved = ArgumentCaptor.forClass(EconomicTerm.class);
        verify(termRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getTerm()).isEqualTo("단리");
        assertThat(saved.getValue().isActive()).isTrue();
    }

    @Test
    @DisplayName("이미 있는 용어이거나 동시 등록으로 DB 제약에 걸리면 TERM_DUPLICATED")
    void createDuplicate() {
        when(termRepository.existsByTerm("복리")).thenReturn(true);
        assertCode(() -> service.create(new TermCreateDTO("복리", "뜻")), TermErrorCode.TERM_DUPLICATED);
        verify(termRepository, never()).saveAndFlush(any());

        when(termRepository.existsByTerm("인플레이션")).thenReturn(false);
        when(termRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));
        assertCode(() -> service.create(new TermCreateDTO("인플레이션", "뜻")), TermErrorCode.TERM_DUPLICATED);
    }

    @Test
    @DisplayName("수정: 용어·뜻·사용 여부를 바꾼다")
    void updates() {
        when(termRepository.existsByTermAndEconomicTermIdNot("복리", 3L)).thenReturn(false);

        service.update(3L, new TermUpdateDTO(" 복리 ", "이자에 이자가 붙는 방식", false));

        assertThat(term.getTerm()).isEqualTo("복리");
        assertThat(term.getDescription()).isEqualTo("이자에 이자가 붙는 방식");
        assertThat(term.isActive()).isFalse();
    }

    @Test
    @DisplayName("대소문자만 바꾸면 내 행이 중복으로 걸리지 않도록 중복 검사를 건너뛴다")
    void caseOnlyChangeSkipsDuplicateCheck() {
        service.update(3L, new TermUpdateDTO("compound", "복리", true));

        assertThat(term.getTerm()).isEqualTo("compound");
        verify(termRepository, never()).existsByTermAndEconomicTermIdNot(anyString(), anyLong());
    }

    @Test
    @DisplayName("다른 용어와 겹치게 바꾸면 TERM_DUPLICATED, 없는 용어면 TERM_NOT_FOUND")
    void updateErrors() {
        when(termRepository.existsByTermAndEconomicTermIdNot("복리", 3L)).thenReturn(true);
        assertCode(() -> service.update(3L, new TermUpdateDTO("복리", "뜻", true)), TermErrorCode.TERM_DUPLICATED);
        assertThat(term.getTerm()).isEqualTo("Compound");

        when(termRepository.findById(99L)).thenReturn(Optional.empty());
        assertCode(() -> service.update(99L, new TermUpdateDTO("x", "y", true)), TermErrorCode.TERM_NOT_FOUND);
    }

    @Test
    @DisplayName("삭제: 노출 이력이 없으면 지운다")
    void deletesWithoutHistory() {
        when(scheduleRepository.existsByEconomicTermId(3L)).thenReturn(false);

        service.delete(3L);

        verify(termRepository).delete(term);
    }

    @Test
    @DisplayName("삭제: 노출 이력이 있으면 지우지 않고 비활성으로 둔다")
    void deactivatesWithHistory() {
        when(scheduleRepository.existsByEconomicTermId(3L)).thenReturn(true);

        service.delete(3L);

        verify(termRepository, never()).delete(any(EconomicTerm.class));
        assertThat(term.isActive()).isFalse();
    }

    @Test
    @DisplayName("없는 용어를 지우면 TERM_NOT_FOUND")
    void deleteNotFound() {
        when(termRepository.findById(99L)).thenReturn(Optional.empty());

        assertCode(() -> service.delete(99L), TermErrorCode.TERM_NOT_FOUND);
        verify(termRepository, never()).delete(any(EconomicTerm.class));
        verify(scheduleRepository, never()).existsByEconomicTermId(eq(99L));
    }
}
