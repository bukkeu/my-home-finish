package com.wanted.moneybeetle.domain.trading.model.dto;

/* comment.
*   담당 : PM 이재성 (모의투자)
*   종목 등록(API 125)·수정(API 126) 요청 : 분야, 이름, 시작 가격, 기업정보(회사 소개·주요 사업·가상 실적)
*  */

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class StockSaveDTO {

    @NotNull
    private Long sectorId;

    @NotBlank
    @Size(max = 50)
    private String name;

    @Min(1)
    private int startPrice;

    // DB TEXT 는 65,535바이트까지. 한 글자가 최대 4바이트(UTF-8)라 16,000자면 넘지 않는다
    @Size(max = 16000)
    private String intro;

    @Size(max = 300)
    private String business;

    @Size(max = 50)
    private String revenue;

    @Size(max = 50)
    private String operatingProfit;
}
