package com.wanted.moneybeetle.domain.trading.model.dto;

/* comment.
*   담당 : PM 이재성 (모의투자)
*   턴별 가격 저장 요청 (API 128) : confirm(경고가 있어도 저장할지), 바꿀 칸 목록
*  */

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PriceSaveDTO {

    private boolean confirm;

    @NotEmpty
    @Valid
    private List<PriceCellDTO> cells;
}
