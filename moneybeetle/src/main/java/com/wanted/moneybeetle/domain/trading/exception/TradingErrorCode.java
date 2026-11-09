package com.wanted.moneybeetle.domain.trading.exception;

/* comment.
*   담당 : PM (모의투자)
*   모의투자 에러 코드 (API 명세서 4장). 필요한 코드는 각 이슈에서 추가한다
*   - #24 시세·뉴스 조회 : NO_RUNNING_SEASON
*   - 앞으로 추가 : ALREADY_JOINED, JOIN_CLOSED, NOT_ENOUGH_QUANTITY, STALE_TURN_PRICE, MARKET_HOLIDAY,
*     SEASON_CLOSED, RANKING_NOT_PUBLISHED, NEXT_TURN_NOT_READY, PAST_TURN_LOCKED, CSV_INVALID, INVALID_STATUS_TRANSITION
*  */

import com.wanted.moneybeetle.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TradingErrorCode implements ErrorCode {

    NO_RUNNING_SEASON(HttpStatus.NOT_FOUND, "다음 시즌을 준비 중이에요");                                 // 404

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
