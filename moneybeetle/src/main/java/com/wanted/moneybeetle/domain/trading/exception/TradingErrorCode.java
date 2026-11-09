package com.wanted.moneybeetle.domain.trading.exception;

/* comment.
*   담당 : PM (모의투자)
*   모의투자 에러 코드 (API 명세서 4장). 필요한 코드는 각 이슈에서 추가한다
*   - #41 분야·종목 관리 : SECTOR_DUPLICATED, STOCK_DUPLICATED, STOCK_LIMIT_EXCEEDED
*   - 앞으로 추가 : NO_RUNNING_SEASON, ALREADY_JOINED, JOIN_CLOSED, NOT_ENOUGH_QUANTITY, STALE_TURN_PRICE,
*     MARKET_HOLIDAY, SEASON_CLOSED, RANKING_NOT_PUBLISHED, NEXT_TURN_NOT_READY, PAST_TURN_LOCKED, CSV_INVALID,
*     INVALID_STATUS_TRANSITION, START_DATE_NOT_MONDAY (#40 시즌 관리는 #136)
*  */

import com.wanted.moneybeetle.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TradingErrorCode implements ErrorCode {

    SECTOR_DUPLICATED(HttpStatus.CONFLICT, "이미 있는 분야 이름이에요"),                                 // 409
    STOCK_DUPLICATED(HttpStatus.CONFLICT, "이미 있는 종목 이름이에요"),                                  // 409
    STOCK_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "한 분야에는 종목을 2개까지 둘 수 있어요");                  // 409

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
