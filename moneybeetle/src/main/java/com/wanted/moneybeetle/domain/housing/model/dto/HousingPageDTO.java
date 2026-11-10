package com.wanted.moneybeetle.domain.housing.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

/* comment.
*   담당 : SCM (모의 청약)
*   모의 청약 화면(/user/demo-housing-subscription)을 처음 그릴 때 필요한 값 (#58)
*   - level·unlockLevel·locked : Lv.12 미만이면 잠금 안내를 보여 주고 레벨이 필요한 영역을 흐리게(블러) 한다 (SP-24, NFR-09)
*   - today : 캘린더가 처음 보여 줄 달, 오늘 표시 (서울 날짜)
*   나머지 데이터(청약통장·캘린더·신청 내역)는 화면이 API(63~71)로 불러온다
*  */
@Getter
@AllArgsConstructor
public class HousingPageDTO {

    private int level;
    private int unlockLevel;
    private boolean locked;
    private LocalDate today;
}
