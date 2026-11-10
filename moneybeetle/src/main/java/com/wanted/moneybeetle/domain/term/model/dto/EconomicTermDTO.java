package com.wanted.moneybeetle.domain.term.model.dto;

import com.wanted.moneybeetle.domain.term.model.entity.EconomicTerm;

/* comment.
*   담당 : DBA ② (경제 용어)
*   오늘의 경제 용어 한 건 (API 20 terms) { economicTermId, term, description }
*  */
public record EconomicTermDTO(Long economicTermId, String term, String description) {

    public static EconomicTermDTO from(EconomicTerm term) {
        return new EconomicTermDTO(term.getEconomicTermId(), term.getTerm(), term.getDescription());
    }
}
