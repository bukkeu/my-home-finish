package com.wanted.moneybeetle.domain.member.model.dto;

import lombok.Getter;

/* comment.
*   담당 : DBA ② (회원·인증)
*   이메일·닉네임 중복 확인 결과 (API 2, 3) { "available": true }
*  */
@Getter
public class AvailabilityDTO {

    private final boolean available;

    private AvailabilityDTO(boolean available) {
        this.available = available;
    }

    public static AvailabilityDTO of(boolean available) {
        return new AvailabilityDTO(available);
    }
}
