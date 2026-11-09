package com.wanted.moneybeetle.domain.trading.model.dto;

/* comment.
*   담당 : PM 이재성 (모의투자)
*   시즌 설정 변경 요청 (API 116) : 이월 비율(%)·이월 상한(없으면 null)·참가 해금 레벨
*  */

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SeasonSettingDTO {

    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("100.00")
    private BigDecimal carryoverRate;

    @PositiveOrZero
    private Long carryoverCap;

    @NotNull
    @Min(1)
    @Max(20)
    private Integer unlockLevel;
}
