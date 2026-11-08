package com.wanted.moneybeetle.domain.trading.model.dto;

/* comment.
*   담당 : PM 이재성 (모의투자)
*   가격 저장 결과 (API 128·130) : 저장한 칸 수, 이상값 경고(±30% 초과)
*   - 경고가 있고 confirm=false 면 저장하지 않고 saved=0 으로 경고만 돌려준다
*  */

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PriceSaveResultDTO {

    private int saved;
    private List<Warning> warnings;

    @Getter
    @AllArgsConstructor
    public static class Warning {
        private Long stockId;
        private int turnNo;
        private String message;
    }
}
