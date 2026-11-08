package com.wanted.moneybeetle.domain.trading.exception;

/* comment.
*   담당 : PM 이재성 (모의투자)
*   모의투자 에러 코드 (API 명세서 4장). 필요한 코드는 각 이슈에서 추가한다
*   - #40 시즌 관리 : START_DATE_NOT_MONDAY, INVALID_STATUS_TRANSITION, SEASON_CLOSED
*   - 앞으로 추가 : NO_RUNNING_SEASON, ALREADY_JOINED, JOIN_CLOSED, NOT_ENOUGH_QUANTITY, STALE_TURN_PRICE,
*     MARKET_HOLIDAY, RANKING_NOT_PUBLISHED, NEXT_TURN_NOT_READY, PAST_TURN_LOCKED, CSV_INVALID
*  */

import com.wanted.moneybeetle.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TradingErrorCode implements ErrorCode {

    START_DATE_NOT_MONDAY(HttpStatus.BAD_REQUEST, "시즌 시작일은 월요일이어야 해요"),                       // 400
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "지금 상태에서는 그 상태로 바꿀 수 없어요"),              // 409
    SEASON_CLOSED(HttpStatus.CONFLICT, "시즌이 끝났어요. 결과를 기다려 주세요");                           // 409

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
