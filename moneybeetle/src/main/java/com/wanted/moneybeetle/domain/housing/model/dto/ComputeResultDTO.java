package com.wanted.moneybeetle.domain.housing.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/* comment.
*   담당 : SCM (모의 청약)
*   당첨자 산정 결과 (API 147) - { "applicants": 8, "winners": 3, "computedAt": "..." }
*  */
@Getter
@AllArgsConstructor
public class ComputeResultDTO {

    private int applicants;
    private int winners;
    private LocalDateTime computedAt;
}
