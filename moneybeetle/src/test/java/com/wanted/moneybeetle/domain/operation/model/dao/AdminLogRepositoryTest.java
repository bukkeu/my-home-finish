package com.wanted.moneybeetle.domain.operation.model.dao;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanted.moneybeetle.domain.operation.model.entity.AdminLog;
import com.wanted.moneybeetle.global.config.MoneyBeetleApplication;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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
    @DisplayName("대상 ID 와 detail 은 비워도 저장된다")
    void savesWithoutTargetAndDetail() {
        AdminLog saved = repository.saveAndFlush(AdminLog.create(1L, "RANKING_PUBLISH", "SEASON", null, null));
        entityManager.clear();

        AdminLog found = repository.findById(saved.getAdminLogId()).orElseThrow();
        assertThat(found.getTargetId()).isNull();
        assertThat(found.getDetail()).isNull();
    }
}
