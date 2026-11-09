package com.wanted.moneybeetle.domain.notification.model.dao;

import com.wanted.moneybeetle.domain.notification.model.entity.Notification;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/* comment.
*   담당 : DBA ② (알림)
*   테이블 notification
*  */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 내 알림 최신순 (API 25) */
    Page<Notification> findByMemberIdOrderByCreatedAtDescNotificationIdDesc(Long memberId, Pageable pageable);

    /** 안 읽은 알림 수 (API 25 unreadCount) */
    long countByMemberIdAndReadFalse(Long memberId);

    /** 내 알림 하나. 남의 알림 ID 는 찾지 못한다 (NFR-03) */
    Optional<Notification> findByNotificationIdAndMemberId(Long notificationId, Long memberId);

    /** 내 안 읽은 알림을 모두 읽음으로 바꾼다 (API 27). 바꾼 건수를 돌려준다 */
    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.read = true where n.memberId = :memberId and n.read = false")
    int markAllRead(@Param("memberId") Long memberId);
}
