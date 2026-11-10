package com.wanted.moneybeetle.domain.housing.model.dto;

import com.wanted.moneybeetle.domain.housing.model.entity.HousingComplex;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

/* comment.
*   담당 : SCM (모의 청약)
*   관리자 단지 목록 한 줄 (API 142) - 단지 정보, 일정, 오늘 기준 상태, 신청자 수
*  */
@Getter
@AllArgsConstructor
public class AdminComplexDTO {

    private Long complexId;
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
    private String status;
    private long applicants;          // 신청자 수 (있으면 일정·세대 수 수정과 삭제가 막힌다)

    public static AdminComplexDTO from(HousingComplex complex, LocalDate today, long applicants) {
        return new AdminComplexDTO(complex.getId(), complex.getName(), complex.getLocation(), complex.getAreaType(),
                complex.getSalePrice(), complex.getSupplyUnits(), complex.getSupplyType(),
                complex.getNoticeDate(), complex.getApplyStartDate(), complex.getApplyEndDate(),
                complex.getAnnounceDate(), complex.currentStatus(today), applicants);
    }
}
