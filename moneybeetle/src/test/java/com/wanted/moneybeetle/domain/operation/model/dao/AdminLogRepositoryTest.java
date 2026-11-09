package com.wanted.moneybeetle.domain.operation.model.dao;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanted.moneybeetle.domain.operation.model.entity.AdminLog;
import com.wanted.moneybeetle.global.config.MoneyBeetleApplication;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

@EntityScan(basePackages = "com.wanted.moneybeetle.domain.operation.model.entity")
@EnableJpaRepositories(basePackages = "com.wanted.moneybeetle.domain.operation.model.dao")
@ContextConfiguration(classes = MoneyBeetleApplication.class)
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-log-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AdminLogRepositoryTest {

    @Autowired
    private AdminLogRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("기록을 저장하면 생성 시각이 채워지고, JSON detail 은 그대로 다시 읽힌다")
    void savesWithJsonDetail() {
        String detail = "{\"before\":\"PENDING\",\"after\":\"APPROVED\"}";

        AdminLog saved = repository.saveAndFlush(AdminLog.create(1L, "COURSE_APPROVE", "COURSE", 33L, detail));
        entityManager.clear();   // 저장한 객체가 아니라 H2 에서 다시 읽은 값을 확인한다 (JSON 매핑·DB 기본값 검증)
        AdminLog found = repository.findById(saved.getAdminLogId()).orElseThrow();

        assertThat(found).isNotSameAs(saved);
        assertThat(found.getAction()).isEqualTo("COURSE_APPROVE");
        assertThat(found.getTargetType()).isEqualTo("COURSE");
        assertThat(found.getTargetId()).isEqualTo(33L);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getDetail()).contains("\"after\"").contains("APPROVED");
    }

    @Test
    @DisplayName("대상·관리자·기간으로 최신순 조회하고, null 조건은 쓰지 않는다")
    void searchFilters() {
        repository.saveAndFlush(AdminLog.create(1L, "TURN_ADVANCE", "SEASON", 7L, null));
        repository.saveAndFlush(AdminLog.create(2L, "SETTLE", "SEASON", 7L, null));
        repository.saveAndFlush(AdminLog.create(1L, "COURSE_APPROVE", "COURSE", 33L, null));
        var page = PageRequest.of(0, 50);

        assertThat(repository.search(null, null, null, null, null, page).getContent()).hasSize(3);
        var season = repository.search("SEASON", 7L, null, null, null, page).getContent();
        assertThat(season).extracting(AdminLog::getAction).containsExactly("SETTLE", "TURN_ADVANCE");   // 최신순
        assertThat(repository.search(null, null, 1L, null, null, page).getContent()).hasSize(2);
        assertThat(repository.search("COURSE", 33L, 2L, null, null, page).getContent()).isEmpty();

        LocalDateTime now = LocalDateTime.now();
        assertThat(repository.search(null, null, null, now.minusDays(1), now.plusDays(1), page).getContent()).hasSize(3);
        assertThat(repository.search(null, null, null, now.plusDays(1), null, page).getContent()).isEmpty();
        assertThat(repository.search(null, null, null, null, now.minusDays(1), page).getContent()).isEmpty();
    }

    @Test
    @DisplayName("대상 ID 와 detail 은 비워도 저장된다")
    void savesWithoutTargetAndDetail() {
        AdminLog saved = repository.saveAndFlush(AdminLog.create(1L, "RANKING_PUBLISH", "SEASON", null, null));
        entityManager.clear();

        AdminLog found = repository.findById(saved.getAdminLogId()).orElseThrow();
        assertThat(found.getTargetId()).isNull();
        assertThat(found.getDetail()).isNull();
    }
}
