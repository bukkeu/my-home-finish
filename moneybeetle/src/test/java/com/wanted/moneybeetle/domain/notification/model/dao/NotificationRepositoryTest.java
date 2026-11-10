package com.wanted.moneybeetle.domain.notification.model.dao;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanted.moneybeetle.domain.notification.model.entity.Notification;
import com.wanted.moneybeetle.MoneyBeetleApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

@EntityScan(basePackages = "com.wanted.moneybeetle.domain.notification.model.entity")
@EnableJpaRepositories(basePackages = "com.wanted.moneybeetle.domain.notification.model.dao")
@ContextConfiguration(classes = MoneyBeetleApplication.class)
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:notification-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository repository;

    @Test
    @DisplayName("해당 회원의 읽지 않은 알림만 읽음 처리한다")
    void markAllRead() {
        Notification unread1 = Notification.create(7L, "UNLOCK", "알림 1", null);
        Notification unread2 = Notification.create(7L, "UNLOCK", "알림 2", null);
        Notification read = Notification.create(7L, "UNLOCK", "알림 3", null);
        read.markRead();
        Notification other = Notification.create(8L, "UNLOCK", "알림 4", null);

        repository.save(unread1);
        repository.save(unread2);
        repository.save(read);
        repository.save(other);
        repository.flush();

        int updatedCount = repository.markAllRead(7L);

        assertThat(updatedCount).isEqualTo(2);
        assertThat(repository.findByNotificationIdAndMemberId(
                unread1.getNotificationId(), 7L).orElseThrow().isRead()).isTrue();
        assertThat(repository.findByNotificationIdAndMemberId(
                unread2.getNotificationId(), 7L).orElseThrow().isRead()).isTrue();
        assertThat(repository.findByNotificationIdAndMemberId(
                read.getNotificationId(), 7L).orElseThrow().isRead()).isTrue();
        assertThat(repository.findByNotificationIdAndMemberId(
                other.getNotificationId(), 8L).orElseThrow().isRead()).isFalse();
    }

    @Test
    @DisplayName("읽지 않은 알림이 없는 회원은 0건이 갱신된다")
    void markAllReadReturnsZeroWhenNothingToUpdate() {
        Notification read = Notification.create(7L, "UNLOCK", "알림", null);
        read.markRead();

        repository.saveAndFlush(read);

        int updatedCount = repository.markAllRead(7L);

        assertThat(updatedCount).isZero();
    }
}
