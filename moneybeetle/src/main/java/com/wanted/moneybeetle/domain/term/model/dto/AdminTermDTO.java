package com.wanted.moneybeetle.domain.term.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wanted.moneybeetle.domain.term.model.entity.EconomicTerm;

/* comment.
*   담당 : DBA ② (경제 용어)
*   관리자 경제 용어 목록의 한 건 (API 95 items) { economicTermId, term, description, isActive }
*  */
public record AdminTermDTO(Long economicTermId, String term, String description,
                           @JsonProperty("isActive") boolean isActive) {

    public static AdminTermDTO from(EconomicTerm term) {
        return new AdminTermDTO(term.getEconomicTermId(), term.getTerm(), term.getDescription(), term.isActive());
    }
}
