package com.wanted.moneybeetle.domain.trading.model.dto;

/* comment.
*   담당 : PM (모의투자)
*   가격표 한 칸 입력 (API 128) : stockId, turnNo 와 price 또는 changeRate(지난 턴 대비 %) 중 하나
*   - 둘 다 있으면 price 를 쓴다
*  */

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PriceCellDTO {

    @NotNull
    private Long stockId;

    @Min(1)
    @Max(12)
    private int turnNo;

    private Integer price;

    private BigDecimal changeRate;

    @AssertTrue(message = "price 또는 changeRate 중 하나는 있어야 해요")
    public boolean isPriceOrRateGiven() {
        return price != null || changeRate != null;
    }

    public static PriceCellDTO ofPrice(Long stockId, int turnNo, int price) {
        return new PriceCellDTO(stockId, turnNo, price, null);
    }
}
