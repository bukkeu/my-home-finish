package com.wanted.moneybeetle.domain.housing.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/* comment.
*   담당 : SCM (모의 청약)
*   관리자 공고 등록(API 143)·수정(API 144) 요청
*   입력값만 보고 판단하는 규칙(빈 값, 1 이상, 일정 순서)은 이 DTO 가 답한다 (CLAUDE.md "규칙의 위치")
*  */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ComplexRequestDTO {

    private String name;
    private String location;
    private String areaType;
    private long salePrice;
    private int supplyUnits;
    private String supplyType;
    private LocalDate noticeDate;
    private LocalDate applyStartDate;
    private LocalDate applyEndDate;
    private LocalDate announceDate;

    // 글자 칸은 비어 있지 않고, 분양가·공급 세대 수는 1 이상이고, 날짜 4개가 모두 있는가
    public boolean isFilled() {
        return hasText(name) && hasText(location) && hasText(areaType) && hasText(supplyType)
                && salePrice > 0 && supplyUnits > 0
                && noticeDate != null && applyStartDate != null && applyEndDate != null && announceDate != null;
    }

    // 일정 순서 : 공고일 ≤ 신청 시작일 ≤ 신청 마감일 ≤ 발표일 (#47 완료 조건)
    public boolean isScheduleInOrder() {
        return !noticeDate.isAfter(applyStartDate)
                && !applyStartDate.isAfter(applyEndDate)
                && !applyEndDate.isAfter(announceDate);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
