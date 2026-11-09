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

    // 입력 상한 (SP-71). 점수는 15년·6명에서 만점이라 상한을 넘는 값은 오입력으로 보고, 큰 값의 int 오버플로도 막는다
    public static final int MAX_HOMELESS_YEARS = 50;
    public static final int MAX_DEPENDENTS = 20;

    private int homelessYears;   // 무주택 기간(년). 1년 미만은 0
    private int dependents;      // 부양가족 수

    // 무주택 기간 0~50년, 부양가족 0~20명 (입력값만으로 판단하는 규칙)
    public boolean isInRange() {
        return homelessYears >= 0 && homelessYears <= MAX_HOMELESS_YEARS
                && dependents >= 0 && dependents <= MAX_DEPENDENTS;
    }
}
