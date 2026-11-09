package com.wanted.moneybeetle.domain.trading.model.dto;

/* comment.
*   담당 : PM (모의투자)
*   시즌 상태 변경 요청 (API 117) : READY / RUNNING / HOLIDAY / SETTLING / ENDED
*  */

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SeasonStatusDTO {

    @NotBlank
    private String status;
}
