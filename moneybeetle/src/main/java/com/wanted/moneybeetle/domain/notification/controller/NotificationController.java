package com.wanted.moneybeetle.domain.notification.controller;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.notification.model.dto.NotificationListDTO;
import com.wanted.moneybeetle.domain.notification.model.service.NotificationService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/* comment.
*   담당 : DBA ② (알림)
*   GET /api/v1/notifications, PATCH /notifications/{notificationId}/read, PATCH /notifications/read-all
*   - 로그인한 회원 자신의 알림만 다룬다 (회원 번호는 로그인 정보에서 가져오고 요청으로 받지 않는다)
*   - 목록의 쪽 크기는 기본 20, 최대 50 이다.
*  */
@RestController
@RequiredArgsConstructor
public class NotificationController {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 50;

    private final NotificationService notificationService;

    // API 25. 알림 목록 (안 읽은 알림 수 + 최신순 목록)
    @GetMapping("/api/v1/notifications")
    public ApiResponse<NotificationListDTO> getNotifications(
            @AuthenticationPrincipal AuthDetails me,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_SIZE) int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_SIZE));
        return ApiResponse.success(notificationService.findNotifications(me.getMemberId(), pageable));
    }

    // API 26. 알림 읽음 (내 알림이 아니면 404)
    @PatchMapping("/api/v1/notifications/{notificationId}/read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal AuthDetails me, @PathVariable Long notificationId) {
        notificationService.markRead(me.getMemberId(), notificationId);
        return ResponseEntity.noContent().build();
    }

    // API 27. 알림 모두 읽음
    @PatchMapping("/api/v1/notifications/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal AuthDetails me) {
        notificationService.markAllRead(me.getMemberId());
        return ResponseEntity.noContent().build();
    }
}
