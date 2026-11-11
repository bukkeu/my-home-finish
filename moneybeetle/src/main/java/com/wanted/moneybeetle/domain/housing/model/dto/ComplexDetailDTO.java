package com.wanted.moneybeetle.domain.housing.model.dto;

import com.wanted.moneybeetle.domain.housing.model.entity.HousingComplex;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

/* comment.
 *   담당 : SCM (모의 청약)  : 단지 하나를 자세히
 *   공고 상세 (API 64) - 단지 정보, 일정, 오늘 기준 상태, 경쟁률(접수 마감 뒤에만), 내 신청 내역
 *  */
@Getter
@AllArgsConstructor
public class ComplexDetailDTO {

    private Long complexId;
    private String name;
    private String location;
    private String areaType;
    private long salePrice;
    private int supplyUnits;
    private String supplyType;
    private LocalDate applyStartDate;
    private LocalDate applyEndDate;
    private LocalDate announceDate;
    private String status;
    private Double competitionRate;          // 접수 마감 전에는 null (SP-74, SUB-10)
    private MyApplicationDTO myApplication;  // 신청하지 않았으면 null (SUB-08)

    public static ComplexDetailDTO from(HousingComplex complex, LocalDate today,
                                        Double competitionRate, MyApplicationDTO myApplication) {
        return new ComplexDetailDTO(
                complex.getHousingComplexId(),
                complex.getName(),
                complex.getLocation(),
                complex.getAreaType(),
                complex.getSalePrice(),
                complex.getSupplyUnits(),
                complex.getSupplyType(),
                complex.getApplyStartDate(),
                complex.getApplyEndDate(),
                complex.getAnnounceDate(),
                complex.currentStatus(today),
                competitionRate,
                myApplication
        );
    }
}