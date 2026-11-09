package com.wanted.moneybeetle.domain.growth.model.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanted.moneybeetle.domain.growth.exception.GrowthErrorCode;
import com.wanted.moneybeetle.domain.growth.model.dto.AdminCashChangeDTO;
import com.wanted.moneybeetle.domain.growth.model.dto.CashAdjustDTO;
import com.wanted.moneybeetle.domain.growth.model.dto.CashAdjustResultDTO;
import com.wanted.moneybeetle.domain.operation.model.service.AdminLogService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* comment.
*   담당 : DBA ② (성장·캐시·레벨)
*   관리자 캐시 수동 지급·회수 (이슈 #39, API 110, ADM-30, SP-17)
*   - 사유는 필수(REASON_REQUIRED). 회수로 잔액이 0 미만이 되면 거절(NOT_ENOUGH_CASH).
*   - 금액 범위·사유를 확인한 뒤 RewardService.adjustByAdmin 에 맡긴다. 회원 행 잠금, 잔액 확인, 캐시 변경, 내역(cash_history) 저장은
*     그쪽이 하고(캐시를 바꾸는 길은 RewardService 하나), 이 서비스는 작업 기록(admin_log, CASH_GIVE·CASH_TAKE)을 같은 트랜잭션에서 남긴다.
*     어느 하나가 실패하면 모두 되돌려진다.
*  */
@Service
@RequiredArgsConstructor
public class CashAdjustService {

    /**
     * 한 번에 지급·회수할 수 있는 최대 금액 10억 캐시 (정책 정의서 SP-17, 팀 합의). 입력 실수로 0이 더 붙은 금액과
     * 잔액(long) 오버플로를 함께 막는다. 넘으면 INVALID_AMOUNT 다. 상한을 운영 중에 바꿀 일이 생기면 서비스 설정(system_setting)으로 옮긴다.
     */
    public static final long MAX_AMOUNT = 1_000_000_000L;

    static final String ACTION_GIVE = "CASH_GIVE";
    static final String ACTION_TAKE = "CASH_TAKE";
    static final String TARGET_TYPE = "MEMBER";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final RewardService rewardService;
    private final AdminLogService adminLogService;

    /**
     * @throws BusinessException INVALID_AMOUNT(금액이 0 이하이거나 상한 초과), REASON_REQUIRED(사유 없음),
     *                           NOT_ENOUGH_CASH(회수할 캐시가 모자람), MEMBER_NOT_FOUND(없는 회원)
     */
    @Transactional
    public CashAdjustResultDTO adjust(Long adminId, Long memberId, CashAdjustDTO request) {
        long amount = request.amount();
        if (amount <= 0 || amount > MAX_AMOUNT) {
            throw new BusinessException(GrowthErrorCode.INVALID_AMOUNT);
        }
        String reason = request.reason() == null ? "" : request.reason().strip();
        if (reason.isEmpty()) {
            throw new BusinessException(GrowthErrorCode.REASON_REQUIRED);
        }

        // 회원 잠금·잔액 확인·캐시 변경·내역 저장은 RewardService 가 한다 (캐시를 바꾸는 길은 그 서비스 하나). 같은 트랜잭션이다
        AdminCashChangeDTO change = rewardService.adjustByAdmin(adminId, memberId, request.isGive(), amount, reason);

        adminLogService.record(adminId, request.isGive() ? ACTION_GIVE : ACTION_TAKE, TARGET_TYPE, memberId,
                toJson(amount, reason, change.balanceBefore(), change.balanceAfter()));
        return new CashAdjustResultDTO(change.cashHistoryId(), change.balanceAfter());
    }

    private static String toJson(long amount, String reason, long balanceBefore, long balanceAfter) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("amount", amount);
        detail.put("reason", reason);
        detail.put("balanceBefore", balanceBefore);
        detail.put("balanceAfter", balanceAfter);
        try {
            return JSON.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("작업 기록을 만들지 못했습니다.", e);
        }
    }
}
