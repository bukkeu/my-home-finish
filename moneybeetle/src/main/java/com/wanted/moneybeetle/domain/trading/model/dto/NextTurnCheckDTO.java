package com.wanted.moneybeetle.domain.trading.model.dto;

import java.time.LocalDate;
import java.util.List;

/* comment.
*   담당 : PM (#44, #193)
*   다음 턴 점검 결과 (API 119). 대시보드 요약(#193)도 같은 값을 쓴다.
*   nextTradeDate : 다음 턴의 예정 거래일. 예정일보다 이르면 화면이 넘길지 확인을 받는다 (SP-50 하루 1턴, 막지는 않는다)
*   ready : 다음 턴이 있고, 휴장이 아니고, 사용 중 종목이 있고 그 가격이 모두 있고, 뉴스가 1개 이상 있을 때
*  */
public record NextTurnCheckDTO(boolean ready, Integer nextTurn, LocalDate nextTradeDate, List<String> missingPrices, long newsCount,
        boolean isHoliday) {
}
