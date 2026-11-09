package com.wanted.moneybeetle.domain.housing.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/* comment.
*   담당 : SCM (모의 청약)
*   가점 계산기(API 68)·청약 신청(API 69) 요청 - { "homelessYears": 3, "dependents": 1 }
*   납입회차는 사용자가 입력하지 않고 내 청약통장 값을 쓴다
*  */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScoreRequestDTO {

    private int homelessYears;   // 무주택 기간(년). 1년 미만은 0
    private int dependents;      // 부양가족 수
}
