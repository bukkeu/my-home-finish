package com.wanted.moneybeetle.domain.operation.model.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanted.moneybeetle.domain.operation.model.entity.AdminLog;
import java.time.LocalDateTime;

/* comment.
*   담당 : DBA ② (관리자 운영)
*   관리자 작업 기록 한 건 (API 113 응답). detail 은 JSON 문자열이 아니라 JSON 객체로 내보낸다.
*  */
public record AdminLogDTO(Long adminLogId, String adminNickname, String action, String targetType, Long targetId,
                          JsonNode detail, LocalDateTime createdAt) {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static AdminLogDTO of(AdminLog log, String adminNickname) {
        return new AdminLogDTO(log.getAdminLogId(), adminNickname, log.getAction(), log.getTargetType(),
                log.getTargetId(), parse(log.getDetail()), log.getCreatedAt());
    }

    private static JsonNode parse(String detail) {
        if (detail == null) {
            return null;
        }
        try {
            return JSON.readTree(detail);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
