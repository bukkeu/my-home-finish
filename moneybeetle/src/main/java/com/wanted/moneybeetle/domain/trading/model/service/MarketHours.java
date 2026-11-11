package com.wanted.moneybeetle.domain.trading.model.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/* comment.
*   담당 : PM (모의투자)
*   #208 정규장 시간 : 08:00 ~ 20:00 (한국 시간). 여는 시각은 포함, 닫는 시각은 포함하지 않는다
*   - 07:59:59 닫힘 / 08:00:00 열림 / 19:59:59 열림 / 20:00:00 닫힘
*   - 장 시간 밖(20:00 ~ 다음 날 08:00)에는 주문을 받지 않는다 (SP-58). 관리자는 장 마감 뒤 다음 턴을 준비해 넘긴다
*   - 다음 턴은 다음 거래일 08:00 에 시작하는 것으로 보여 준다 (홈 API 53 의 nextTurnAt)
*   시각은 호출하는 쪽이 공통 Clock(Asia/Seoul)으로 구해 넘긴다
*  */
public final class MarketHours {

    public static final LocalTime OPEN = LocalTime.of(8, 0);
    public static final LocalTime CLOSE = LocalTime.of(20, 0);

    private MarketHours() {
    }

    /** 지금 장이 열려 있는가 (08:00 이상 20:00 미만) */
    public static boolean isOpen(LocalDateTime now) {
        LocalTime time = now.toLocalTime();
        return !time.isBefore(OPEN) && time.isBefore(CLOSE);
    }

    /** 그 거래일의 장 시작 시각 */
    public static LocalDateTime opensAt(LocalDate tradeDate) {
        return tradeDate.atTime(OPEN);
    }

    /** 그 거래일의 장 마감 시각 */
    public static LocalDateTime closesAt(LocalDate tradeDate) {
        return tradeDate.atTime(CLOSE);
    }
}
