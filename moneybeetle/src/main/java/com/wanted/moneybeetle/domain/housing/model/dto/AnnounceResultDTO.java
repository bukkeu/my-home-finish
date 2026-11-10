package com.wanted.moneybeetle.domain.housing.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/* comment.
*   담당 : SCM (모의 청약)
*   결과 발표 (API 148) - { "announcedAt": "...", "notified": 8 }  notified = 알림을 보낸 신청자 수
*  */
@Getter
@AllArgsConstructor
public class AnnounceResultDTO {

    private LocalDateTime announcedAt;
    private int notified;
}
