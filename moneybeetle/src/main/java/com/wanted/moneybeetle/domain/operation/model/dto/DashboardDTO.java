package com.wanted.moneybeetle.domain.operation.model.dto;

/* comment.
*   담당 : DBA ② (관리자 운영)
*   관리자 대시보드 응답 (API 84, ADM-08)
*   - season 은 진행 중인 시즌이 없으면 null 이다.
*   - season 의 todayOrders·nextTurnReady·missing 은 trading 도메인이 조회 메서드를 공개하기 전까지 null 이다 (#33 후속).
*  */
public record DashboardDTO(long reviewWaiting, long inquiryWaiting, Season season, Members members) {

    public record Season(Long seasonId, int currentTurn, long participants, Long todayOrders,
                         Boolean nextTurnReady, Missing missing) {
    }

    /** 다음 턴에 비어 있는 데이터 수 (가격이 없는 종목 수, 뉴스가 없는 종목 수 등) */
    public record Missing(int prices, int news) {
    }

    public record Members(long total, long withdrawn) {
    }
}
