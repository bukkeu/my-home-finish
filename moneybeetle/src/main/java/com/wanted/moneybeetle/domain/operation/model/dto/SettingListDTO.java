package com.wanted.moneybeetle.domain.operation.model.dto;

import java.util.List;

/* comment.
*   담당 : DBA ② (관리자 운영)
*   서비스 설정 목록 (API 111 응답) { settings: [...] }
*  */
public record SettingListDTO(List<SettingDTO> settings) {
}
