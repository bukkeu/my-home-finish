package com.wanted.moneybeetle.domain.trading.model.dto;

/* comment.
*   담당 : PM 이재성 (모의투자)
*   시즌 생성 요청 (API 115) : 이름, 시작일(월요일), 설정을 복사할 이전 시즌
*   - 시작일이 월요일인지는 입력값만 보고 판단하므로 DTO 가 검사한다
*  */

import com.wanted.moneybeetle.domain.trading.exception.TradingErrorCode;
import com.wanted.moneybeetle.global.exception.BusinessException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SeasonSaveDTO {

    @NotBlank
    @Size(max = 50)
    private String name;

    @NotNull
    private LocalDate startDate;

    /** 이 시즌의 이월·해금 설정을 그대로 가져온다 (없으면 기본값) */
    private Long copyFromSeasonId;

    public void checkStartDate() {
        if (startDate == null || startDate.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new BusinessException(TradingErrorCode.START_DATE_NOT_MONDAY);
        }
    }
}
