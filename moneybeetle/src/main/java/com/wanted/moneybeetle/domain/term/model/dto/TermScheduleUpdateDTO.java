package com.wanted.moneybeetle.domain.term.model.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/* comment.
*   담당 : DBA ② (경제 용어)
*   오늘의 용어 지정 요청 (API 100) { economicTermIds: [5개] } - 개수·중복·존재 여부는 TermService 가 에러 코드로 알려 준다.
*  */
public record TermScheduleUpdateDTO(@NotNull List<Long> economicTermIds) {
}
