package com.wanted.moneybeetle.domain.term.model.service;

import com.wanted.moneybeetle.domain.term.exception.TermErrorCode;
import com.wanted.moneybeetle.domain.term.model.dao.EconomicTermRepository;
import com.wanted.moneybeetle.domain.term.model.dao.TermScheduleRepository;
import com.wanted.moneybeetle.domain.term.model.dto.AdminTermDTO;
import com.wanted.moneybeetle.domain.term.model.dto.AdminTermListDTO;
import com.wanted.moneybeetle.domain.term.model.dto.TermCreateDTO;
import com.wanted.moneybeetle.domain.term.model.dto.TermCreatedDTO;
import com.wanted.moneybeetle.domain.term.model.dto.TermUpdateDTO;
import com.wanted.moneybeetle.domain.term.model.entity.EconomicTerm;
import com.wanted.moneybeetle.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* comment.
*   담당 : DBA ② (경제 용어)
*   관리자 경제 용어 관리 (이슈 #36, API 95~98, ADM-05)
*   - 용어는 중복될 수 없다 (TERM_DUPLICATED). 동시에 같은 용어를 등록하면 DB UNIQUE 로 한쪽이 막히고 같은 오류로 알려 준다.
*   - 삭제: 오늘의 용어로 노출된 적이 있으면 지우지 않고 사용 여부를 끈다 (일정의 용어 참조를 지키려고). 없으면 지운다.
*   - 한 쪽 크기는 기본 50, 최대 100 이다.
*  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EconomicTermService {

    static final int DEFAULT_SIZE = 50;
    static final int MAX_SIZE = 100;

    private final EconomicTermRepository economicTermRepository;
    private final TermScheduleRepository termScheduleRepository;

    public AdminTermListDTO findTerms(String keyword, Integer page, Integer size) {
        int pageNo = page == null ? 0 : Math.max(page, 0);
        int pageSize = size == null ? DEFAULT_SIZE : Math.min(Math.max(size, 1), MAX_SIZE);
        String word = keyword == null || keyword.isBlank() ? null : keyword.strip();
        return new AdminTermListDTO(economicTermRepository.search(word, PageRequest.of(pageNo, pageSize))
                .map(AdminTermDTO::from)
                .getContent());
    }

    @Transactional
    public TermCreatedDTO create(TermCreateDTO request) {
        String term = request.term().strip();
        if (economicTermRepository.existsByTerm(term)) {
            throw new BusinessException(TermErrorCode.TERM_DUPLICATED);
        }
        try {
            return new TermCreatedDTO(economicTermRepository
                    .saveAndFlush(EconomicTerm.create(term, request.description())).getEconomicTermId());
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(TermErrorCode.TERM_DUPLICATED);
        }
    }

    @Transactional
    public void update(Long termId, TermUpdateDTO request) {
        EconomicTerm term = getTerm(termId);
        String newTerm = request.term().strip();
        // 대소문자만 바꾸면 DB 정렬 규칙(대소문자 구분 없음) 때문에 내 행이 중복으로 걸리므로 그때는 중복 검사를 건너뛴다
        if (!newTerm.equalsIgnoreCase(term.getTerm())
                && economicTermRepository.existsByTermAndEconomicTermIdNot(newTerm, termId)) {
            throw new BusinessException(TermErrorCode.TERM_DUPLICATED);
        }
        term.change(newTerm, request.description());
        if (request.isActive()) {
            term.activate();
        } else {
            term.deactivate();
        }
        try {
            economicTermRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(TermErrorCode.TERM_DUPLICATED);
        }
    }

    @Transactional
    public void delete(Long termId) {
        EconomicTerm term = getTerm(termId);
        if (termScheduleRepository.existsByEconomicTermId(termId)) {
            term.deactivate();
        } else {
            economicTermRepository.delete(term);
        }
    }

    private EconomicTerm getTerm(Long termId) {
        return economicTermRepository.findById(termId)
                .orElseThrow(() -> new BusinessException(TermErrorCode.TERM_NOT_FOUND));
    }
}
