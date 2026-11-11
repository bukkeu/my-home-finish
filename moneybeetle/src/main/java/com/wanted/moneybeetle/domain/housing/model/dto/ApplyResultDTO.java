package com.wanted.moneybeetle.domain.housing.model.dto;

import com.wanted.moneybeetle.domain.housing.model.entity.HousingApplication;
import lombok.AllArgsConstructor;
import lombok.Getter;

/* comment.
*   담당 : SCM (모의 청약)
*   청약 신청 결과 (API 69) - { applicationId, receiptNo, totalScore }
*  */
@Getter
@AllArgsConstructor
public class ApplyResultDTO {

    private Long applicationId;
    private String receiptNo;
    private int totalScore;

    public static ApplyResultDTO from(HousingApplication application) {
        return new ApplyResultDTO(application.getId(), application.getReceiptNo(), application.getTotalScore());
    }
}
