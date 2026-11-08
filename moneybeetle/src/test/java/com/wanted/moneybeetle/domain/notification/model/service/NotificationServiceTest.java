package com.wanted.moneybeetle.domain.notification.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanted.moneybeetle.domain.notification.model.dao.NotificationRepository;
import com.wanted.moneybeetle.domain.notification.model.dto.NotificationDTO;
import com.wanted.moneybeetle.domain.notification.model.dto.NotificationListDTO;
import com.wanted.moneybeetle.domain.notification.model.entity.Notification;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.repository.query.parser.PartTree;

/* comment.
*   담당 : PM (성장·홈·모의 청약)
*   NotificationService · Notification · NotificationDTO - Repository 를 Mock 으로 두고 서버·DB 없이 돈다
*  */
class NotificationServiceTest {

    private NotificationRepository repository;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        service = new NotificationService(repository);
    }

    @Test
    @DisplayName("send: 받는 회원·종류·제목·이동 주소로 알림을 저장한다 (처음에는 안 읽음)")
    void sendSavesNotification() {
        service.send(7L, Notification.TYPE_INQUIRY_REPLY, "문의에 답변이 등록됐어요", "/user/inquiry/3");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getMemberId()).isEqualTo(7L);
        assertThat(saved.getType()).isEqualTo("INQUIRY_REPLY");
        assertThat(saved.getTitle()).isEqualTo("문의에 답변이 등록됐어요");
        assertThat(saved.getLinkUrl()).isEqualTo("/user/inquiry/3");
        assertThat(saved.isRead()).isFalse();
    }

    @Test
    @DisplayName("send: 이동 주소는 비워 둘 수 있다")
    void linkUrlIsOptional() {
        service.send(7L, Notification.TYPE_UNLOCK, "모의투자가 열렸어요", null);

        verify(repository).save(any(Notification.class));
    }

    @Test
    @DisplayName("send: 잘못된 값(회원 없음·빈 종류·빈 제목·긴 제목·바깥 주소)은 저장하지 않고 IllegalArgumentException")
    void sendRejectsInvalidValues() {
        assertThatThrownBy(() -> service.send(null, "UNLOCK", "제목", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.send(7L, " ", "제목", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.send(7L, "UNLOCK", "", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.send(7L, "UNLOCK", "가".repeat(101), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.send(7L, "U".repeat(31), "제목", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.send(7L, "UNLOCK", "제목", "https://evil.example"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.send(7L, "UNLOCK", "제목", "//evil.example"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.send(7L, "UNLOCK", "제목", "javascript:alert(1)"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("findNotifications: 안 읽은 수와 현재 페이지의 알림을 함께 돌려준다")
    void findNotifications() {
        var pageable = PageRequest.of(0, 20);
        when(repository.findByMemberIdOrderByCreatedAtDescNotificationIdDesc(7L, pageable))
                .thenReturn(new PageImpl<>(List.of(Notification.create(7L, "UNLOCK", "열렸어요", "/user/home")), pageable, 1));
        when(repository.countByMemberIdAndReadFalse(7L)).thenReturn(2L);

        NotificationListDTO result = service.findNotifications(7L, pageable);

        assertThat(result.getUnreadCount()).isEqualTo(2L);
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getTitle()).isEqualTo("열렸어요");
    }

    @Test
    @DisplayName("markRead: 내 알림이면 읽음으로 바뀌고, 이미 읽은 알림에 다시 불러도 그대로다")
    void markRead() {
        Notification mine = Notification.create(7L, "UNLOCK", "열렸어요", null);
        when(repository.findByNotificationIdAndMemberId(5L, 7L)).thenReturn(Optional.of(mine));

        service.markRead(7L, 5L);
        service.markRead(7L, 5L);

        assertThat(mine.isRead()).isTrue();
    }

    @Test
    @DisplayName("markRead: 없거나 남의 알림이면 NOT_FOUND(404)")
    void markReadNotFound() {
        when(repository.findByNotificationIdAndMemberId(5L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(7L, 5L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("markAllRead: 내 회원 ID로만 일괄 읽음 처리한다")
    void markAllRead() {
        service.markAllRead(7L);

        verify(repository).markAllRead(7L);
    }

    @Test
    @DisplayName("DTO 는 JSON 필드를 API 명세서대로 isRead 로 내보낸다")
    void dtoJsonUsesIsRead() throws Exception {
        String json = new ObjectMapper().findAndRegisterModules()
                .writeValueAsString(NotificationDTO.from(Notification.create(7L, "UNLOCK", "열렸어요", "/user/home")));

        assertThat(json).contains("\"isRead\":false").doesNotContain("\"read\"");
    }

    @Test
    @DisplayName("Repository 쿼리 메서드 이름이 Entity 필드(memberId, read, notificationId)로 해석된다")
    void queryNamesResolve() {
        assertThat(new PartTree("countByMemberIdAndReadFalse", Notification.class).getParts())
                .extracting(p -> p.getProperty().getSegment()).containsExactly("memberId", "read");
        assertThat(new PartTree("findByNotificationIdAndMemberId", Notification.class).getParts())
                .extracting(p -> p.getProperty().getSegment()).containsExactly("notificationId", "memberId");
        assertThat(new PartTree("findByMemberIdOrderByCreatedAtDescNotificationIdDesc", Notification.class).getParts())
                .extracting(p -> p.getProperty().getSegment()).containsExactly("memberId");
    }
}
