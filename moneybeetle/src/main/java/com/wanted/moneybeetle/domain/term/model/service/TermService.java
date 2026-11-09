package com.wanted.moneybeetle.domain.term.model.service;

import com.wanted.moneybeetle.domain.term.exception.TermErrorCode;
import com.wanted.moneybeetle.domain.term.model.dao.EconomicTermRepository;
import com.wanted.moneybeetle.domain.term.model.dao.TermScheduleRepository;
import com.wanted.moneybeetle.domain.term.model.dto.EconomicTermDTO;
import com.wanted.moneybeetle.domain.term.model.dto.ScheduledTermDTO;
import com.wanted.moneybeetle.domain.term.model.dto.TermScheduleDayDTO;
import com.wanted.moneybeetle.domain.term.model.dto.TermScheduleListDTO;
import com.wanted.moneybeetle.domain.term.model.dto.TodayTermsDTO;
import com.wanted.moneybeetle.domain.term.model.entity.EconomicTerm;
import com.wanted.moneybeetle.domain.term.model.entity.TermSchedule;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/* comment.
*   담당 : DBA ② (경제 용어)
*   오늘의 용어 5개와 날짜별 지정 (이슈 #36, API 20·99·100, SP-45~47)
*   - getToday : 오늘(KST) 일정을 돌려준다. 관리자가 지정하지 않은 날이면 처음 볼 때 자동으로 정해 저장한다 (TermAutoSelector).
*       홈 화면 API(#13)가 이 메서드를 그대로 부른다.
*   - findMonth : 그 달에 일정이 있는 날의 현황 (지정·자동 구분)
*   - assign : 한 날짜를 서로 다른 용어 5개로 지정한다. 지난 날짜는 바꿀 수 없다 (오늘은 가능).
*       기존 일정(자동 선정 포함)을 지우고 새로 넣는다.
*  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TermService {

    private final TermScheduleRepository termScheduleRepository;
    private final EconomicTermRepository economicTermRepository;
    private final TermAutoSelector termAutoSelector;
    private final Clock clock;

    /**
     * 트랜잭션 밖에서 실행한다. 자동 선정이 동시에 겹쳐 실패했을 때 저장된 값을 다시 읽으려면,
     * 이 메서드가 이미 읽기 트랜잭션(REPEATABLE READ 스냅샷)을 잡고 있으면 안 되기 때문이다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TodayTermsDTO getToday() {
        LocalDate today = LocalDate.now(clock);
        List<TermSchedule> rows = termScheduleRepository.findByShowDateOrderBySortOrderAsc(today);
        if (rows.isEmpty()) {
            try {
                rows = termAutoSelector.select(today);
            } catch (DataIntegrityViolationException e) {
                rows = termScheduleRepository.findByShowDateOrderBySortOrderAsc(today);   // 다른 요청이 먼저 정했다
            }
        }
        Map<Long, EconomicTerm> terms = termsById(rows);
        return new TodayTermsDTO(today, rows.stream()
                .map(row -> terms.get(row.getEconomicTermId()))
                .map(EconomicTermDTO::from)
                .toList());
    }

    /** 그 달에 일정이 있는 날을 날짜순으로 (API 99) */
    public TermScheduleListDTO findMonth(YearMonth month) {
        List<TermSchedule> rows = termScheduleRepository.findByShowDateBetweenOrderByShowDateAscSortOrderAsc(
                month.atDay(1), month.atEndOfMonth());
        Map<Long, EconomicTerm> terms = termsById(rows);

        Map<LocalDate, List<TermSchedule>> byDate = new LinkedHashMap<>();
        for (TermSchedule row : rows) {
            byDate.computeIfAbsent(row.getShowDate(), date -> new ArrayList<>()).add(row);
        }
        return new TermScheduleListDTO(byDate.entrySet().stream()
                .map(entry -> new TermScheduleDayDTO(entry.getKey(), entry.getValue().get(0).isAuto(),
                        entry.getValue().stream()
                                .map(row -> terms.get(row.getEconomicTermId()))
                                .map(term -> new ScheduledTermDTO(term.getEconomicTermId(), term.getTerm()))
                                .toList()))
                .toList());
    }

    /**
     * 한 날짜의 오늘의 용어를 지정한다 (API 100).
     *
     * @throws BusinessException PAST_DATE_LOCKED(지난 날짜), TERMS_NOT_ENOUGH(5개 미만),
     *                           TERM_SELECTION_INVALID(5개 초과·중복), TERM_NOT_FOUND(없거나 사용 중이 아닌 용어)
     */
    @Transactional
    public void assign(LocalDate date, List<Long> economicTermIds) {
        if (date.isBefore(LocalDate.now(clock))) {
            throw new BusinessException(TermErrorCode.PAST_DATE_LOCKED);
        }
        if (economicTermIds.stream().anyMatch(Objects::isNull)) {
            throw new BusinessException(TermErrorCode.TERM_SELECTION_INVALID);
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>(economicTermIds);
        if (distinct.size() < TermSchedule.DAILY_COUNT) {
            throw new BusinessException(TermErrorCode.TERMS_NOT_ENOUGH);
        }
        if (distinct.size() != economicTermIds.size() || distinct.size() > TermSchedule.DAILY_COUNT) {
            throw new BusinessException(TermErrorCode.TERM_SELECTION_INVALID);
        }
        if (economicTermRepository.findByEconomicTermIdInAndActiveTrue(distinct).size() != distinct.size()) {
            throw new BusinessException(TermErrorCode.TERM_NOT_FOUND);
        }

        try {
            termScheduleRepository.deleteByShowDate(date);
            int order = 1;
            for (Long termId : distinct) {
                termScheduleRepository.save(TermSchedule.create(date, termId, order++, false));
            }
            termScheduleRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(CommonErrorCode.DUPLICATE_REQUEST);   // 다른 관리자가 같은 날짜를 동시에 지정했다
        }
    }

    private Map<Long, EconomicTerm> termsById(List<TermSchedule> rows) {
        List<Long> ids = rows.stream().map(TermSchedule::getEconomicTermId).distinct().toList();
        return economicTermRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(EconomicTerm::getEconomicTermId, Function.identity()));
    }
}
