package com.wanted.moneybeetle.domain.trading.model.dto;

/* comment.
*   담당 : PM (#193)
*   관리자 대시보드(API 84)용 진행 중 시즌 요약 : 오늘 주문 수와 다음 턴 점검 결과
*   missing.prices 는 다음 턴에 가격이 비어 있는 종목 수, missing.news 는 뉴스가 하나도 없으면 1 아니면 0
*  */
public record TradingDashboardSummaryDTO(Long seasonId, long todayOrders, boolean nextTurnReady, Missing missing) {

    public record Missing(int prices, int news) {
    }
}
