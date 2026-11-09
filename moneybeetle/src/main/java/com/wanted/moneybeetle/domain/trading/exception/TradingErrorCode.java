package com.wanted.moneybeetle.domain.trading.exception;

/* comment.
*   담당 : PM (모의투자)
*   모의투자 에러 코드 (API 명세서 4장). 필요한 코드는 각 이슈에서 추가한다
*   - #43 뉴스 관리 : PAST_TURN_LOCKED
*   - 앞으로 추가 : NO_RUNNING_SEASON, ALREADY_JOINED, JOIN_CLOSED, NOT_ENOUGH_QUANTITY, STALE_TURN_PRICE,
*     MARKET_HOLIDAY, SEASON_CLOSED, RANKING_NOT_PUBLISHED, NEXT_TURN_NOT_READY, CSV_INVALID, INVALID_STATUS_TRANSITION
*  */

import com.wanted.moneybeetle.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TradingErrorCode implements ErrorCode {

    PAST_TURN_LOCKED(HttpStatus.CONFLICT, "지난 턴의 가격은 수정할 수 없어요");                            // 409 (지난 턴 뉴스도 같은 코드, SP-60)

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
