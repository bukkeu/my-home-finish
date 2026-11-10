package com.wanted.moneybeetle.domain.trading.model.dto;

/* comment.
*   담당 : PM (모의투자)
*   뉴스 등록(API 132)·수정(API 133) 요청 : 공개 턴, 제목, 본문, 관련 분야·종목, 관리자 메모
*  */

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NewsSaveDTO {

    @NotNull
    @Min(1)
    @Max(12)   // 시즌은 12턴 (SP-50, TradingSeason.DEFAULT_TOTAL_TURNS)
    private Integer turnNo;

    @NotBlank
    @Size(max = 100)
    private String title;

    // DB TEXT 는 65,535바이트까지. 한 글자가 최대 4바이트(UTF-8)라 16,000자면 넘지 않는다
    @NotBlank
    @Size(max = 16000)
    private String content;

    /** 관련 분야 (없으면 빈 목록) */
    private List<@NotNull Long> sectorIds;

    /** 관련 종목 (없으면 빈 목록) */
    private List<@NotNull Long> stockIds;

    @Size(max = 500)
    private String adminMemo;

    public List<Long> getSectorIds() {
        return sectorIds == null ? List.of() : sectorIds;
    }

    public List<Long> getStockIds() {
        return stockIds == null ? List.of() : stockIds;
    }
}
