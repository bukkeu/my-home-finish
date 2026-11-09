package com.wanted.moneybeetle.domain.housing.model.dto;

import com.wanted.moneybeetle.domain.housing.model.entity.HousingApplication;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/* comment.
 *   담당 : SCM (모의 청약)  :  상세(접수번호, 총점, 신청 시각 -> 신청 안 했으면 모두 null)
 *   공고 상세(API 64)의 내 신청 정보 - 신청하지 않았으면 이 DTO 대신 null
 *  */
@Getter
@AllArgsConstructor
public class MyApplicationDTO {

    private Long applicationId;
    private String receiptNo;
    private int totalScore;
    private LocalDateTime appliedAt;

    public static MyApplicationDTO from(HousingApplication application) {
        return new MyApplicationDTO(
                application.getId(),
                application.getReceiptNo(),
                application.getTotalScore(),
                application.getAppliedAt()
        );
    }
}