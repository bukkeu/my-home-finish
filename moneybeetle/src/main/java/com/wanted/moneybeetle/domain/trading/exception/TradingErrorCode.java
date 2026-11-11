package com.wanted.moneybeetle.domain.trading.exception;

/* comment.
*   담당 : PM (모의투자)
*   모의투자 에러 코드 (API 명세서 4장). 필요한 코드는 각 이슈에서 추가한다
*   - #23 홈·시즌 참가 : NO_RUNNING_SEASON, ALREADY_JOINED, JOIN_CLOSED, PREVIOUS_SEASON_NOT_SETTLED
*   - #43 뉴스 관리 : PAST_TURN_NEWS_LOCKED
*   - #41 분야·종목 관리 : SECTOR_DUPLICATED, STOCK_DUPLICATED, STOCK_LIMIT_EXCEEDED
*   - #42 턴별 가격 : PAST_TURN_LOCKED, PRICE_NOT_POSITIVE, PRICE_OUT_OF_RANGE, CSV_INVALID
*   - #40 시즌 관리 : START_DATE_NOT_MONDAY, INVALID_STATUS_TRANSITION, SEASON_CLOSED
*   - #24 시세·뉴스 조회 : NO_RUNNING_SEASON
*   - #111 턴 진행 규칙 : MARKET_HOLIDAY, LAST_TURN_REACHED
*   - #44 턴 진행 : NEXT_TURN_NOT_READY, TURN_CONFLICT
*   - #207 시즌 순서 : SEASON_PERIOD_OVERLAP (PREVIOUS_SEASON_NOT_SETTLED 는 #23 과 같은 코드를 쓴다)
*   - #25 매수·매도 : NOT_JOINED, NOT_ENOUGH_CASH, NOT_ENOUGH_QUANTITY, REASON_REQUIRED, STALE_TURN_PRICE
*   - #26 시즌 랭킹 : RANKING_NOT_PUBLISHED
*  */

import com.wanted.moneybeetle.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TradingErrorCode implements ErrorCode {

    NO_RUNNING_SEASON(HttpStatus.NOT_FOUND, "다음 시즌을 준비 중이에요"),                                          // 404
    ALREADY_JOINED(HttpStatus.CONFLICT, "이미 이번 시즌에 참가했어요"),                                             // 409
    JOIN_CLOSED(HttpStatus.CONFLICT, "이번 시즌 참가는 마감됐어요"),                                                // 409
    PREVIOUS_SEASON_NOT_SETTLED(HttpStatus.CONFLICT, "이전 시즌 정산이 끝나면 참가할 수 있어요"),                       // 409 (SP-63)
    PAST_TURN_NEWS_LOCKED(HttpStatus.CONFLICT, "지난 턴 뉴스는 오타 수정만 할 수 있어요"),                              // 409 (SP-60, ADM-27)
    SECTOR_DUPLICATED(HttpStatus.CONFLICT, "이미 있는 분야 이름이에요"),                                           // 409
    STOCK_DUPLICATED(HttpStatus.CONFLICT, "이미 있는 종목 이름이에요"),                                            // 409
    STOCK_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "한 분야에는 종목을 2개까지 둘 수 있어요"),                               // 409
    PAST_TURN_LOCKED(HttpStatus.CONFLICT, "지난 턴의 가격은 수정할 수 없어요"),                                       // 409
    PRICE_NOT_POSITIVE(HttpStatus.BAD_REQUEST, "가격은 1원 이상이어야 해요"),                                      // 400
    PRICE_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "변동률이 너무 커서 가격을 계산할 수 없어요"),                             // 400
    CSV_INVALID(HttpStatus.BAD_REQUEST, "CSV 형식이 맞지 않는 행이 있어요"),                                        // 400
    START_DATE_NOT_MONDAY(HttpStatus.BAD_REQUEST, "시즌 시작일은 월요일이어야 해요"),                                 // 400
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "지금 상태에서는 그 상태로 바꿀 수 없어요"),                          // 409
    SEASON_CLOSED(HttpStatus.CONFLICT, "시즌이 끝났어요. 결과를 기다려 주세요"),                                        // 409
    MARKET_HOLIDAY(HttpStatus.CONFLICT, "오늘은 휴장일이에요. 다음 거래일에 다시 거래할 수 있어요"),                            // 409
    LAST_TURN_REACHED(HttpStatus.CONFLICT, "마지막 턴이라 더 넘길 수 없어요. 정산을 진행해 주세요"),                          // 409
    NEXT_TURN_NOT_READY(HttpStatus.CONFLICT, "다음 턴의 가격이나 뉴스가 아직 다 준비되지 않았어요"),                          // 409
    TURN_CONFLICT(HttpStatus.CONFLICT, "다른 관리자가 이미 턴을 넘겼어요. 화면을 새로 고쳐 주세요"),                            // 409
    SEASON_PERIOD_OVERLAP(HttpStatus.CONFLICT, "기간이 겹치는 시즌이 있어요. 시작일을 다시 정해 주세요"),                            // 409
    NOT_JOINED(HttpStatus.FORBIDDEN, "이번 시즌에 참가하지 않았어요. 먼저 참가해 주세요"),                              // 403
    NOT_ENOUGH_CASH(HttpStatus.BAD_REQUEST, "현금이 부족해요"),                                                    // 400
    NOT_ENOUGH_QUANTITY(HttpStatus.BAD_REQUEST, "보유한 수량보다 많이 팔 수 없어요"),                                 // 400
    REASON_REQUIRED(HttpStatus.BAD_REQUEST, "매매 이유를 골라 주세요"),                                             // 400
    STALE_TURN_PRICE(HttpStatus.CONFLICT, "가격이 바뀌었어요. 현재 가격을 확인해 주세요"),                             // 409
    RANKING_NOT_PUBLISHED(HttpStatus.FORBIDDEN, "랭킹은 시즌이 끝나면 공개돼요"),                                       // 403
    NOT_SETTLED(HttpStatus.CONFLICT, "정산을 먼저 해 주세요");                                                    // 409                                       // 403

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
