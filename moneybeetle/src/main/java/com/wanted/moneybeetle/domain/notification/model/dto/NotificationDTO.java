package com.wanted.moneybeetle.domain.notification.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wanted.moneybeetle.domain.notification.model.entity.Notification;
import java.time.LocalDateTime;
import lombok.Getter;

/* comment.
*   담당 : DBA ② (알림)
*   알림 한 줄 (API 25 items)
*   - boolean 필드의 getter 는 isRead() 라서 Jackson 이 "read" 로 내보낸다. 명세서대로 "isRead" 로 고정한다
*  */
@Getter
public class NotificationDTO {

    private final Long notificationId;
    private final String type;
    private final String title;
    private final String linkUrl;
    private final boolean read;
    private final LocalDateTime createdAt;

    private NotificationDTO(Long notificationId, String type, String title, String linkUrl,
                            boolean read, LocalDateTime createdAt) {
        this.notificationId = notificationId;
        this.type = type;
        this.title = title;
        this.linkUrl = linkUrl;
        this.read = read;
        this.createdAt = createdAt;
    }

    public static NotificationDTO from(Notification notification) {
        return new NotificationDTO(
                notification.getNotificationId(),
                notification.getType(),
                notification.getTitle(),
                notification.getLinkUrl(),
                notification.isRead(),
                notification.getCreatedAt());
    }

    @JsonProperty("isRead")
    public boolean isRead() {
        return read;
    }
}
