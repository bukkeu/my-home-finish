package com.wanted.moneybeetle.domain.trading.model.dto;

/* comment.
*   담당 : PM 이재성 (모의투자)
*   관리자 분야 목록의 한 줄 (API 121)
*  */

import com.wanted.moneybeetle.domain.trading.model.entity.Sector;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SectorDTO {

    private Long sectorId;
    private String name;
    private int sortOrder;
    @JsonProperty("isActive")
    private boolean active;

    public static SectorDTO from(Sector sector) {
        return new SectorDTO(sector.getSectorId(), sector.getName(), sector.getSortOrder(), sector.isActive());
    }
}
