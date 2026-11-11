package com.wanted.moneybeetle.domain.trading.model.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/* comment.
*   담당 : PM (#46)
*   시즌 통계 (API 141, ADM-35) : 평균 수익률(%), 많이 산 종목, 매매 이유 비율(%), 급락 턴에 판 사람 비율(%)
*  */
public record SeasonStatisticsDTO(BigDecimal avgReturnRate, List<String> topBought,
        Map<String, BigDecimal> reasonRatio, BigDecimal panicSellRate) {
}
