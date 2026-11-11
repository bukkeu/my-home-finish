package com.wanted.moneybeetle.domain.trading.model.service;

import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;

/* comment.
*   담당 : PM (모의투자)
*   모의투자 관리자 Service 들이 함께 쓰는 로그인 관리자 확인 (#293)
*   - 시즌 생성·턴 진행·정산은 관리자 번호를 기록(created_by, 관리자 로그)하므로 번호가 없으면 먼저 UNAUTHORIZED 로 거절한다
*  */
final class TradingAdminCheck {

    private TradingAdminCheck() {
    }

    /** 로그인한 관리자 번호가 없으면 UNAUTHORIZED */
    static void requireAdmin(Long adminId) {
        if (adminId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
    }
}
