package com.wanted.moneybeetle.domain.notification.model.dto;

import java.util.List;
import lombok.Getter;

/* comment.
*   담당 : DBA ② (알림)
*   알림 목록 응답 (API 25): 안 읽은 알림 수 + 현재 페이지의 알림
*  */
@Getter
public class NotificationListDTO {

    private final long unreadCount;
    private final List<NotificationDTO> items;

    public NotificationListDTO(long unreadCount, List<NotificationDTO> items) {
        this.unreadCount = unreadCount;
        this.items = items;
    }
}
