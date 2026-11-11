package com.wanted.moneybeetle.domain.trading.model.dto;

/* comment.
*   담당 : PM (모의투자)
*   가격표 한 칸 입력 (API 128) : stockId, turnNo 와 price 또는 changeRate(지난 턴 대비 %) 중 하나
*   - 둘 다 있으면 price 를 쓴다
*  */

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
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

    // -100% 이하는 가격이 0 이하가 되고, 아주 큰 지수 표기 값은 계산에 자원을 많이 쓴다 -> 입력 단계에서 막는다.
    // 범위·자릿수는 DB stock_price.change_rate DECIMAL(6,2) 기준
    @DecimalMin(value = "-100", inclusive = false)
    @DecimalMax("9999.99")
    @Digits(integer = 4, fraction = 2)
    private BigDecimal changeRate;

    @AssertTrue(message = "price 또는 changeRate 중 하나는 있어야 해요")
    public boolean isPriceOrRateGiven() {
        return price != null || changeRate != null;
    }

    public static PriceCellDTO ofPrice(Long stockId, int turnNo, int price) {
        return new PriceCellDTO(stockId, turnNo, price, null);
    }
}
