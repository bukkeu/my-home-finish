package com.wanted.moneybeetle.domain.term.model.dto;

/* comment.
*   담당 : DBA ② (경제 용어)
*   날짜별 일정 안의 용어 한 건 (API 99) { economicTermId, term }
*  */
public record ScheduledTermDTO(Long economicTermId, String term) {
}
