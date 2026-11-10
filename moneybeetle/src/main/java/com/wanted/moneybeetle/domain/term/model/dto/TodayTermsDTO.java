package com.wanted.moneybeetle.domain.term.model.dto;

import java.time.LocalDate;
import java.util.List;

/* comment.
*   담당 : DBA ② (경제 용어)
*   오늘의 경제 용어 (API 20 응답) { date, terms }. 홈 화면 API(#13)도 이 값을 그대로 쓴다.
*  */
public record TodayTermsDTO(LocalDate date, List<EconomicTermDTO> terms) {
}
