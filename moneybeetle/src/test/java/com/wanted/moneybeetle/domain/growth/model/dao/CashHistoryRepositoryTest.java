package com.wanted.moneybeetle.domain.growth.model.dao;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanted.moneybeetle.MoneyBeetleApplication;
import com.wanted.moneybeetle.domain.growth.model.entity.CashHistory;
import com.wanted.moneybeetle.domain.growth.model.entity.CashSource;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

/* comment.
*   담당 : DBA ② (성장·캐시·레벨)
*   CashHistory 의 원인 종류(enum)가 DB 에는 이름 문자열로 저장되고, 이름으로 다시 읽히며, 원인으로 조회되는지 H2 로 확인한다 (이슈 #287)
*  */
@EntityScan(basePackages = "com.wanted.moneybeetle.domain.growth.model.entity")
@EnableJpaRepositories(basePackages = "com.wanted.moneybeetle.domain.growth.model.dao")
@ContextConfiguration(classes = MoneyBeetleApplication.class)
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:cash-history-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CashHistoryRepositoryTest {

    @Autowired
    private CashHistoryRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("원인 종류는 DB 에 enum 이름 문자열로 저장되고 다시 읽으면 같은 enum 이다")
    void storedAsName() {
        CashHistory saved = repository.saveAndFlush(CashHistory.earn(1L, CashSource.RANKING, 71L, null, 50_000, 50_000));
        repository.saveAndFlush(CashHistory.adminGive(1L, 9L, 2_000, 52_000, "보정"));
        entityManager.clear();   // 저장한 객체가 아니라 H2 에서 다시 읽은 값을 확인한다

        Object raw = entityManager.createNativeQuery("select source_type from cash_history where cash_history_id = :id")
                .setParameter("id", saved.getCashHistoryId()).getSingleResult();
        assertThat(raw).isEqualTo("RANKING");
        assertThat(repository.findById(saved.getCashHistoryId()).orElseThrow().getSourceType()).isEqualTo(CashSource.RANKING);
        assertThat(entityManager.createNativeQuery("select source_type from cash_history where history_type = 'ADMIN_GIVE'")
                .getSingleResult()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("옛 데이터처럼 DB 에 문자열이 들어 있어도 같은 이름의 enum 으로 읽힌다")
    void readsLegacyStringRows() {
        CashHistory saved = repository.saveAndFlush(CashHistory.earn(1L, CashSource.ATTENDANCE, 3L, null, 10_000, 10_000));
        entityManager.createNativeQuery("update cash_history set source_type = 'SUBSCRIPTION_DEPOSIT' where cash_history_id = :id")
                .setParameter("id", saved.getCashHistoryId()).executeUpdate();
        entityManager.clear();

        assertThat(repository.findById(saved.getCashHistoryId()).orElseThrow().getSourceType())
                .isEqualTo(CashSource.SUBSCRIPTION_DEPOSIT);
    }

    @Test
    @DisplayName("같은 (회원, 원인 종류, 원인 ID)는 enum 으로 조회된다 (이중 지급·차감 확인)")
    void findsBySource() {
        repository.saveAndFlush(CashHistory.use(1L, CashSource.SEASON_JOIN, 6L, 3_000, 0));

        assertThat(repository.existsByMemberIdAndSourceTypeAndSourceId(1L, CashSource.SEASON_JOIN, 6L)).isTrue();
        assertThat(repository.existsByMemberIdAndSourceTypeAndSourceId(1L, CashSource.SUBSCRIPTION_DEPOSIT, 6L)).isFalse();
        assertThat(repository.findWithLockByMemberIdAndSourceTypeAndSourceId(1L, CashSource.SEASON_JOIN, 6L)).isPresent();
        assertThat(repository.findWithLockByMemberIdAndSourceTypeAndSourceId(2L, CashSource.SEASON_JOIN, 6L)).isEmpty();
    }
}
