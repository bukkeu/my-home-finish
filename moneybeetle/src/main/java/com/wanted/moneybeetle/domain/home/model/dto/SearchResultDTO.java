package com.wanted.moneybeetle.domain.home.model.dto;

/* comment.
*   담당 : PM (통합 검색, #15)
*   통합 검색 결과 (courses, terms, stocks) - API 28
*  */

import com.wanted.moneybeetle.domain.course.model.dto.CourseLabelDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.StockSearchDTO;
import java.util.List;
import lombok.Getter;

@Getter
public class SearchResultDTO {

    private final List<CourseLabelDTO> courses;
    private final List<TermItem> terms;
    private final List<StockSearchDTO> stocks;

    private SearchResultDTO(List<CourseLabelDTO> courses, List<TermItem> terms, List<StockSearchDTO> stocks) {
        this.courses = courses;
        this.terms = terms;
        this.stocks = stocks;
    }

    public static SearchResultDTO of(List<CourseLabelDTO> courses, List<TermItem> terms, List<StockSearchDTO> stocks) {
        return new SearchResultDTO(courses, terms, stocks);
    }

    public static SearchResultDTO empty() {
        return new SearchResultDTO(List.of(), List.of(), List.of());
    }

    /** 경제 용어 한 줄 (API 28 응답 모양) */
    @Getter
    public static class TermItem {

        private final Long economicTermId;
        private final String term;
        private final String description;

        public TermItem(Long economicTermId, String term, String description) {
            this.economicTermId = economicTermId;
            this.term = term;
            this.description = description;
        }
    }
}
