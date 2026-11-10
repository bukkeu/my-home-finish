package com.wanted.moneybeetle.domain.trading.model.dto;

import java.util.List;

/* comment.
*   담당 : PM (#44, #193, #207, #208)
*   다음 턴 점검 결과 (API 119). 대시보드 요약(#193)도 같은 값을 쓴다.
*   ready : 다음 턴이 있고, 휴장이 아니고, 사용 중 종목이 있고 그 가격이 모두 있고, 뉴스가 1개 이상 있고,
*           이전 시즌 정산이 끝났고(#207), 장이 마감된 뒤일 때(#208)
*   previousSeasonSettled : 이전 시즌이 없거나 정산을 마치고 끝났는가
*   afterMarketClose : 턴을 넘길 수 있는 시각인가 - 장(08:00~20:00)이 열려 있지 않거나, 아직 첫 턴을 시작하기 전(0턴)
*  */
public record NextTurnCheckDTO(boolean ready, Integer nextTurn, List<String> missingPrices, long newsCount,
        boolean isHoliday, boolean previousSeasonSettled, boolean afterMarketClose) {
}
