package com.wanted.moneybeetle.domain.housing.model.dto;

import com.wanted.moneybeetle.domain.housing.model.entity.HousingApplication;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/* comment.
*   담당 : SCM (모의 청약)
*   관리자 신청자 목록 한 줄 (API 146) - 닉네임, 신청 시점 가점·납입회차, 산정 결과
*   result·resultRank 는 산정 전이면 null
*  */
@Getter
@AllArgsConstructor
public class ApplicantDTO {

    private Long applicationId;
    private String nickname;
    private String receiptNo;
    private int totalScore;
    private int paidCount;              // 신청 시점 납입회차
    private LocalDateTime appliedAt;
    private String result;              // WIN / LOSE / null
    private Integer resultRank;

    public static ApplicantDTO from(HousingApplication application, String nickname) {
        return new ApplicantDTO(application.getId(), nickname, application.getReceiptNo(),
                application.getTotalScore(), application.getPaidCountSnapshot(), application.getAppliedAt(),
                application.getResult(), application.getResultRank());
    }
}
