package com.wanted.moneybeetle.domain.housing.model.dto;

import com.wanted.moneybeetle.domain.housing.model.entity.HousingApplication;
import lombok.AllArgsConstructor;
import lombok.Getter;

/* comment.
*   담당 : SCM (모의 청약)
*   당첨 결과 (API 71) - { result, totalScore, message }. 발표 뒤에만 만든다
*  */
@Getter
@AllArgsConstructor
public class ApplicationResultDTO {

    private String result;        // WIN / LOSE
    private int totalScore;
    private String message;

    public static ApplicationResultDTO from(HousingApplication application) {
        int score = application.getTotalScore();
        String message = application.isWin()
                ? "축하해요! 당첨이에요. 내 가점은 " + score + "점이었어요"
                : "이번에는 낙첨이에요. 내 가점은 " + score + "점이었어요";   // 정책 정의서 4장 문구
        return new ApplicationResultDTO(application.getResult(), score, message);
    }
}
