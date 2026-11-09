package com.wanted.moneybeetle.domain.housing.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

/* comment.
*   담당 : SCM (모의 청약)
*   가점 계산 결과 (API 68) - 무주택 32 / 부양가족 35 / 통장 17, 합계 84 (SP-71)
*  */
@Getter
@AllArgsConstructor
public class ScoreDTO {

    private int homelessScore;
    private int dependentScore;
    private int accountScore;
    private int totalScore;
    private int maxScore;
    private LocalDate baseDate;   // 계산 기준일 (오늘)
}
