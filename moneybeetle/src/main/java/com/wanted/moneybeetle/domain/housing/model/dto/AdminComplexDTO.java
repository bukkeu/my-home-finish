package com.wanted.moneybeetle.domain.housing.model.dto;

import com.wanted.moneybeetle.domain.housing.model.entity.HousingComplex;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

/* comment.
*   담당 : SCM (모의 청약)
*   관리자 단지 목록 한 줄 (API 142) - 단지 정보, 일정, 오늘 기준 상태, 신청자 수, 산정 여부
*   - computed : 당첨자를 산정했는지 (#63 운영 화면이 "결과 발표" 버튼을 켤지 정할 때 쓴다. 신청자가 0명이어도 산정 기록이 남는다)
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
    private boolean computed;         // 산정 완료 (산정하면 일정·세대 수 수정이 막히고, 발표일부터 발표할 수 있다)

    public static AdminComplexDTO from(HousingComplex complex, LocalDate today, long applicants) {
        return new AdminComplexDTO(complex.getId(), complex.getName(), complex.getLocation(), complex.getAreaType(),
                complex.getSalePrice(), complex.getSupplyUnits(), complex.getSupplyType(),
                complex.getNoticeDate(), complex.getApplyStartDate(), complex.getApplyEndDate(),
                complex.getAnnounceDate(), complex.currentStatus(today), applicants, complex.isComputed());
    }
}
