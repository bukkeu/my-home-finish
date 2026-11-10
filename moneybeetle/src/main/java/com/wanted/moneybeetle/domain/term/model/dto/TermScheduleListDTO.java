package com.wanted.moneybeetle.domain.term.model.dto;

import java.util.List;

/* comment.
*   담당 : DBA ② (경제 용어)
*   월별 지정 현황 (API 99 응답) { days }
*  */
public record TermScheduleListDTO(List<TermScheduleDayDTO> days) {
}
