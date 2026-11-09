package com.wanted.moneybeetle.domain.trading.model.dto;

/* comment.
*   담당 : PM (모의투자)
*   분야 등록(API 122)·수정(API 123) 요청
*   - 등록 : name, sortOrder / 수정 : name, isActive (sortOrder 를 주면 순서도 바꾼다)
*  */

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SectorSaveDTO {

    @NotBlank
    @Size(max = 30)
    private String name;

    @Min(0)
    private Integer sortOrder;

    /** 수정 때만 쓴다. 없으면 바꾸지 않는다 */
    private Boolean isActive;
}
