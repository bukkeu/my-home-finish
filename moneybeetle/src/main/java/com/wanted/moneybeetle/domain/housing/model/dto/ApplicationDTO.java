package com.wanted.moneybeetle.domain.housing.model.dto;

import com.wanted.moneybeetle.domain.housing.model.entity.HousingApplication;
import com.wanted.moneybeetle.domain.housing.model.entity.HousingComplex;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

/* comment.
*   담당 : SCM (모의 청약)
*   내 신청 내역 한 줄 (API 70) - 신청 단지, 접수번호, 가점, 발표일, 결과(발표 전이면 null)
*  */
@Getter
@AllArgsConstructor
public class ApplicationDTO {

    private Long applicationId;
    private String complexName;
    private String receiptNo;
    private int totalScore;
    private LocalDate announceDate;
    private String result;            // WIN / LOSE. 발표 전이면 null (SUB-11)

    // 발표 전에는 결과가 저장돼 있어도 보여 주지 않는다 (SP-75)
    public static ApplicationDTO from(HousingApplication application, HousingComplex complex) {
        String result = complex.isAnnounced() ? application.getResult() : null;
        return new ApplicationDTO(application.getId(), complex.getName(), application.getReceiptNo(),
                application.getTotalScore(), complex.getAnnounceDate(), result);
    }
}
