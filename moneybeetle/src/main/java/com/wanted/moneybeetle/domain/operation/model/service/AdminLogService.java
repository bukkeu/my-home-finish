package com.wanted.moneybeetle.domain.operation.model.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanted.moneybeetle.domain.operation.model.dao.AdminLogRepository;
import com.wanted.moneybeetle.domain.operation.model.entity.AdminLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* comment.
*   담당 : DBA ② (관리자 운영)
*   관리자 작업 기록 창구 (역할분담 문서 §7 연동 약속, DP-43)
*   - record(adminId, action, targetType, targetId, detailJson) : 기록 한 건을 남긴다
*       [호출: 신아림 강의 허가·반려(#35), 이재성 턴 진행·정산·랭킹 공개, 김예린 캐시 지급·회수·계정 정지 등]
*       부르는 쪽 트랜잭션에 함께 묶인다. 그 작업이 되돌려지면 기록도 사라진다 (작업과 기록이 어긋나지 않는다)
*   - action 예 : COURSE_APPROVE / COURSE_REJECT / TURN_ADVANCE / SETTLE / RANKING_PUBLISH / CASH_GIVE
*   - targetType 예 : COURSE / SEASON / MEMBER
*   - detailJson : 변경 전·후 값을 담은 JSON 문자열. 없으면 null
*  */
@Service
@RequiredArgsConstructor
public class AdminLogService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AdminLogRepository adminLogRepository;

    /**
     * 관리자 작업 기록을 한 건 남긴다. 값이 규칙에 맞지 않으면 IllegalArgumentException (AdminLog.create 참고).
     * detailJson 은 비어 있거나 올바른 JSON 이어야 한다.
     */
    @Transactional
    public void record(Long adminId, String action, String targetType, Long targetId, String detailJson) {
        String detail = detailJson == null || detailJson.isBlank() ? null : detailJson;
        requireJson(detail);
        adminLogRepository.save(AdminLog.create(adminId, action, targetType, targetId, detail));
    }

    private static void requireJson(String detail) {
        if (detail == null) {
            return;
        }
        try {
            JSON.readTree(detail);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("detailJson 이 올바른 JSON 이 아닙니다.");
        }
    }
}
