package com.wanted.moneybeetle.domain.notification.model.service;

import com.wanted.moneybeetle.domain.notification.model.dao.NotificationRepository;
import com.wanted.moneybeetle.domain.notification.model.dto.NotificationDTO;
import com.wanted.moneybeetle.domain.notification.model.dto.NotificationListDTO;
import com.wanted.moneybeetle.domain.notification.model.entity.Notification;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* comment.
*   담당 : DBA ② (알림)
*   알림 창구 (역할분담 문서 §7 연동 약속)
*   - send(memberId, type, title, linkUrl) : 알림 한 건을 만든다 [호출: 신아림 검수 결과, 이재성 턴 진행·랭킹 공개, 서수영 청약 결과, 김예린 해금]
*       부르는 쪽 트랜잭션에 함께 묶인다. 그 트랜잭션이 되돌려지면 알림도 사라진다
*   - findNotifications / markRead / markAllRead : 내 알림 조회·읽음 (API 25~27)
*   - 알림 끄기·읽음 표시 같은 화면 편의 설정은 브라우저가 맡고 서버는 모른다 (시스템 설계)
*  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /** 알림 한 건을 만든다. 값이 규칙에 맞지 않으면 IllegalArgumentException (Notification.create 참고) */
    @Transactional
    public void send(Long memberId, String type, String title, String linkUrl) {
        notificationRepository.save(Notification.create(memberId, type, title, linkUrl));
    }

    /** 내 알림을 최신순으로 한 페이지와 안 읽은 알림 수를 함께 돌려준다 (API 25) */
    public NotificationListDTO findNotifications(Long memberId, Pageable pageable) {
        var items = notificationRepository
                .findByMemberIdOrderByCreatedAtDescNotificationIdDesc(memberId, pageable)
                .map(NotificationDTO::from)
                .getContent();
        return new NotificationListDTO(notificationRepository.countByMemberIdAndReadFalse(memberId), items);
    }

    /** 내 알림 하나를 읽음 처리한다 (API 26). 없거나 남의 알림이면 NOT_FOUND(404) */
    @Transactional
    public void markRead(Long memberId, Long notificationId) {
        Notification notification = notificationRepository
                .findByNotificationIdAndMemberId(notificationId, memberId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        notification.markRead();
    }

    /** 내 안 읽은 알림을 모두 읽음 처리한다 (API 27) */
    @Transactional
    public void markAllRead(Long memberId) {
        notificationRepository.markAllRead(memberId);
    }
}
