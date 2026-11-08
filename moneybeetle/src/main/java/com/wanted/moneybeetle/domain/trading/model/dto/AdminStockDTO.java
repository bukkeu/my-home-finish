package com.wanted.moneybeetle.domain.trading.model.dto;

/* comment.
*   담당 : PM 이재성 (모의투자)
*   관리자 종목 목록의 한 줄 (API 124) : 종목, 분야, 시작 가격, 기업정보
*   (사용자 시세 화면의 StockDTO 와 따로 둔다)
*  */

import com.wanted.moneybeetle.domain.trading.model.entity.Stock;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AdminStockDTO {

    private Long stockId;
    private Long sectorId;
    private String sectorName;
    private String name;
    private int startPrice;
    private String intro;
    private String business;
    private String revenue;
    private String operatingProfit;
    @JsonProperty("isActive")
    private boolean active;

    public static AdminStockDTO from(Stock stock, String sectorName) {
        return new AdminStockDTO(
                stock.getStockId(),
                stock.getSectorId(),
                sectorName,
                stock.getName(),
                stock.getStartPrice(),
                stock.getIntro(),
                stock.getBusiness(),
                stock.getRevenue(),
                stock.getOperatingProfit(),
                stock.isActive()
        );
    }
}
