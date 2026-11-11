package com.wanted.moneybeetle.domain.operation.model.dto;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanted.moneybeetle.domain.operation.model.entity.AdminLog;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* comment.
*   담당 : DBA ② (관리자 운영)
*   관리자 작업 기록 한 건 (API 113 응답). detail 은 JSON 문자열이 아니라 JSON 객체로 내보낸다.
*   detail 을 JSON 으로 읽지 못하면 응답에는 null 을 주고(목록 전체가 실패하지 않게), 원인을 warn 로그로 남긴다 (이슈 #299).
*   로그에는 기록 번호와 예외만 남기고 detail 원문은 남기지 않는다: 읽기 도구가 오류 위치에 원문 조각을 싣지 않게 설정한다.
*  */
public record AdminLogDTO(Long adminLogId, String adminNickname, String action, String targetType, Long targetId,
                          JsonNode detail, LocalDateTime createdAt) {

    private static final Logger log = LoggerFactory.getLogger(AdminLogDTO.class);
    private static final ObjectMapper JSON = new ObjectMapper(
            JsonFactory.builder().disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).build());

    public static AdminLogDTO of(AdminLog entry, String adminNickname) {
        return new AdminLogDTO(entry.getAdminLogId(), adminNickname, entry.getAction(), entry.getTargetType(),
                entry.getTargetId(), parse(entry.getAdminLogId(), entry.getDetail()), entry.getCreatedAt());
    }

    private static JsonNode parse(Long adminLogId, String detail) {
        if (detail == null) {
            return null;
        }
        try {
            return JSON.readTree(detail);
        } catch (JsonProcessingException e) {
            log.warn("관리자 로그 상세 변환 실패 adminLogId={}", adminLogId, e);
            return null;
        }
    }
}
