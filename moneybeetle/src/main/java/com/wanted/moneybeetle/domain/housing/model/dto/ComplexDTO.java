package com.wanted.moneybeetle.domain.housing.model.dto;

import com.wanted.moneybeetle.domain.housing.model.entity.HousingComplex;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

/* comment.
 *   담당 : SCM (성장·홈·모의 청약)  :  일정
 *   청약 캘린더 목록의 한 줄 (API 63) - 일정, 오늘 기준 상태, 내 신청 여부
 *  */
@Getter
@AllArgsConstructor
public class ComplexDTO {

    private Long complexId;
    private String name;
    private LocalDate noticeDate;
    private LocalDate applyStartDate;
    private LocalDate applyEndDate;
    private LocalDate announceDate;
    private String status;
    private boolean applied;

    public static ComplexDTO from(HousingComplex complex, LocalDate today, boolean applied) {
        return new ComplexDTO(
                complex.getId(),
                complex.getName(),
                complex.getNoticeDate(),
                complex.getApplyStartDate(),
                complex.getApplyEndDate(),
                complex.getAnnounceDate(),
                complex.currentStatus(today),
                applied
        );
    }
}