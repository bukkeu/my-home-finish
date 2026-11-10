package com.wanted.moneybeetle.domain.operation.model.dto;

import java.util.List;

/* comment.
*   담당 : DBA ② (관리자 운영)
*   관리자 작업 기록 목록 (API 113 응답) { items: [...] }
*  */
public record AdminLogListDTO(List<AdminLogDTO> items) {
}
