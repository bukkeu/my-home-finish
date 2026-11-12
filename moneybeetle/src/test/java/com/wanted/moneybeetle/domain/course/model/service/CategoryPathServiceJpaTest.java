package com.wanted.moneybeetle.domain.course.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wanted.moneybeetle.MoneyBeetleApplication;
import com.wanted.moneybeetle.domain.course.model.dao.CategoryRepository;
import com.wanted.moneybeetle.domain.course.model.entity.Category;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;

/* comment.
*   담당 : DBA ① 신아림 (#349)
*   실제 JPA 저장소(H2)로, snapshot 이 없을 때의 첫 조회가 바깥 트랜잭션이 쥔 연결을 그대로 쓰는지 확인한다
*   - 연결 풀을 1개로, 연결 대기를 0.25초로 줄였다. @DataJpaTest 는 테스트 메서드를 트랜잭션으로 감싸므로 그 1개를 테스트가 이미 쥐고 있다
*   - 이 상태에서 새 연결을 요청하면 0.25초 뒤 실패한다 (다시 읽기용 REQUIRES_NEW 가 실제로 그렇게 실패하는 것도 함께 보인다)
*  */
@EntityScan(basePackages = "com.wanted.moneybeetle.domain.course.model.entity")
@EnableJpaRepositories(basePackages = "com.wanted.moneybeetle.domain.course.model.dao")
@ContextConfiguration(classes = MoneyBeetleApplication.class)
@Import(CategoryPathServiceJpaTest.AuditingConfig.class)
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:category-path-jpa-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.datasource.hikari.connection-timeout=250",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CategoryPathServiceJpaTest {

    @TestConfiguration
    @EnableJpaAuditing(dateTimeProviderRef = "testDateTimeProvider")
    static class AuditingConfig {
        @Bean
        DateTimeProvider testDateTimeProvider() {
            return () -> Optional.of(LocalDateTime.of(2026, 11, 12, 12, 0));
        }
    }

    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("연결 풀 1개를 바깥 트랜잭션이 쥔 상태에서도 첫 조회는 그 연결로 실제 카테고리를 읽는다")
    void firstReadReusesOuterConnection() {
        Category realEstate = categoryRepository.saveAndFlush(Category.createCategory("부동산", 1));
        Category subscription = categoryRepository.saveAndFlush(Category.createSubCategory(realEstate, "청약", 1));
        List<Runnable> pending = new ArrayList<>();
        CategoryPathService service = new CategoryPathService(categoryRepository, transactionManager, pending::add);

        Map<Long, String> paths = service.findCategoryPaths();   // 새 연결을 요청했다면 0.25초 뒤 실패한다

        assertThat(paths).containsEntry(realEstate.getCategoryId(), "부동산")
                .containsEntry(subscription.getCategoryId(), "부동산 › 청약");
        assertThat(pending).hasSize(1);                          // 다시 읽기는 전용 스레드 몫으로 맡겨 두었다

        // 같은 조건에서 새 트랜잭션(REQUIRES_NEW)으로 읽으면 연결을 얻지 못한다 : 요청 스레드에서 다시 읽으면 안 되는 이유
        assertThatThrownBy(service::reload).isInstanceOf(CannotCreateTransactionException.class);
    }
}
