package com.wanted.moneybeetle.domain.term.model.dto;

import java.util.List;

/* comment.
*   담당 : DBA ② (경제 용어)
*   관리자 경제 용어 목록 (API 95 응답) { items }
*  */
public record AdminTermListDTO(List<AdminTermDTO> items) {
}
