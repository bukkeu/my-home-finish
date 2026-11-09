package com.wanted.moneybeetle.domain.term.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

/* comment.
*   담당 : DBA ② (경제 용어)
*   하루의 지정 현황 (API 99 days) { date, isAuto, terms }
*  */
public record TermScheduleDayDTO(LocalDate date, @JsonProperty("isAuto") boolean isAuto,
                                 List<ScheduledTermDTO> terms) {
}
