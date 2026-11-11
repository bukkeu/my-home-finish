package com.wanted.moneybeetle.domain.notification.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/* comment.
*   담당 : DBA ② (알림)
*   테이블 notification
*   - 다른 도메인(member)은 Entity가 아니라 ID로 참조한다
*   - 읽음 처리는 markRead() 로만 바꾼다 (읽음 -> 안 읽음으로 되돌리는 메서드는 두지 않는다)
*  */
@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    /**
     * 종류 (type) 문자열 상수. {@link NotificationType} 으로 옮겼다. 다른 도메인 코드가 아직 쓰고 있어서 남겨 두고,
     * 모두 옮긴 뒤 정리 이슈(#301)에서 지운다. 새 코드는 NotificationType 을 쓴다.
     * 컬럼(type)은 VARCHAR(30) String 그대로다 - 이미 저장된 값이 enum 에 없어도 목록 조회가 깨지지 않게 하려는 것이다.
     */
    /** @deprecated {@link NotificationType#UNLOCK} 을 쓴다 */
    @Deprecated
    public static final String TYPE_UNLOCK = NotificationType.UNLOCK.name();
    /** @deprecated {@link NotificationType#INQUIRY_REPLY} 를 쓴다 */
    @Deprecated
    public static final String TYPE_INQUIRY_REPLY = NotificationType.INQUIRY_REPLY.name();
    /** @deprecated {@link NotificationType#HOUSING_RESULT} 를 쓴다 */
    @Deprecated
    public static final String TYPE_HOUSING_RESULT = NotificationType.HOUSING_RESULT.name();
    /** @deprecated {@link NotificationType#RANKING} 을 쓴다 */
    @Deprecated
    public static final String TYPE_RANKING = NotificationType.RANKING.name();
    /** @deprecated {@link NotificationType#COURSE_REJECTED} 를 쓴다 */
    @Deprecated
    public static final String TYPE_COURSE_REJECTED = NotificationType.COURSE_REJECTED.name();

    public static final int TYPE_MAX_LENGTH = 30;
    public static final int TITLE_MAX_LENGTH = 100;
    public static final int LINK_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long notificationId;

    /** 받는 회원 */
    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false, length = TYPE_MAX_LENGTH)
    private String type;

    @Column(nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    @Column(length = 500)
    private String content;

    /** 눌렀을 때 이동할 화면 (사이트 안 경로) */
    @Column(length = LINK_MAX_LENGTH)
    private String linkUrl;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private Notification(Long memberId, String type, String title, String linkUrl) {
        this.memberId = memberId;
        this.type = type;
        this.title = title;
        this.linkUrl = linkUrl;
    }

    /** 새 알림 (종류를 {@link NotificationType} 으로 받는다). 나머지 규칙은 {@link #create(Long, String, String, String)} 와 같다 */
    public static Notification create(Long memberId, NotificationType type, String title, String linkUrl) {
        if (type == null) {
            throw new IllegalArgumentException("type 은 비어 있을 수 없습니다.");
        }
        return create(memberId, type.name(), title, linkUrl);
    }

    /**
     * 새 알림. 값이 규칙에 맞지 않으면 IllegalArgumentException (호출하는 코드의 실수이지 사용자 입력 오류가 아니다).
     * linkUrl 은 비워 두거나 사이트 안 경로("/user/inquiry/3")여야 한다. 바깥 주소·javascript: 는 거절한다.
     */
    public static Notification create(Long memberId, String type, String title, String linkUrl) {
        if (memberId == null) {
            throw new IllegalArgumentException("받는 회원이 필요합니다.");
        }
        requireText(type, TYPE_MAX_LENGTH, "type");
        requireText(title, TITLE_MAX_LENGTH, "title");
        if (linkUrl != null) {
            if (linkUrl.length() > LINK_MAX_LENGTH) {
                throw new IllegalArgumentException("linkUrl 은 " + LINK_MAX_LENGTH + "자까지 쓸 수 있습니다.");
            }
            if (!linkUrl.startsWith("/") || linkUrl.startsWith("//")) {
                throw new IllegalArgumentException("linkUrl 은 사이트 안 경로('/'로 시작)여야 합니다: " + linkUrl);
            }
            // 브라우저는 역슬래시를 슬래시로 보고("/\evil.com" -> "//evil.com"), 탭·줄바꿈을 지운 뒤 주소를 해석한다
            // ("/<탭>/evil.com" -> "//evil.com"). 그래서 역슬래시와 제어 문자가 든 주소는 모두 거절한다
            if (linkUrl.indexOf('\\') >= 0 || linkUrl.chars().anyMatch(c -> c <= 0x1F || c == 0x7F)) {
                throw new IllegalArgumentException("linkUrl 에 역슬래시나 제어 문자를 쓸 수 없습니다.");
            }
        }
        return new Notification(memberId, type, title, linkUrl);
    }

    /** 읽음 처리. 이미 읽은 알림에 다시 불러도 그대로다 */
    public void markRead() {
        this.read = true;
    }

    private static void requireText(String value, int maxLength, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 은 비어 있을 수 없습니다.");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(name + " 은 " + maxLength + "자까지 쓸 수 있습니다.");
        }
    }
}
