package com.wanted.moneybeetle.domain.trading.model.dao;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanted.moneybeetle.MoneyBeetleApplication;
import com.wanted.moneybeetle.domain.trading.model.entity.TradeOrder;
import com.wanted.moneybeetle.domain.trading.model.entity.TradingSeason;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

/* comment.
*   담당 : PM (모의투자)
*   #208 리뷰 반영 : 주문이 시즌 행을 공유 잠금으로 읽는 쿼리가 실제로 실행되는지,
*   주문 생성 시각이 공통 시계(감시 기능)로 기록되는지 확인한다
*  */
@EntityScan(basePackages = "com.wanted.moneybeetle.domain.trading.model.entity")
@EnableJpaRepositories(basePackages = "com.wanted.moneybeetle.domain.trading.model.dao")
@ContextConfiguration(classes = MoneyBeetleApplication.class)
@Import(TradingSeasonLockRepositoryTest.AuditingConfig.class)
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:trading-season-lock-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TradingSeasonLockRepositoryTest {

    /** 서울 시간으로 고정한 "지금". JpaConfig 의 seoulDateTimeProvider 를 대신한다 */
    static final LocalDateTime SEOUL_NOW = LocalDateTime.of(2026, 11, 5, 8, 30);

    @TestConfiguration
    @EnableJpaAuditing(dateTimeProviderRef = "testDateTimeProvider")
    static class AuditingConfig {
        @Bean
        DateTimeProvider testDateTimeProvider() {
            return () -> Optional.of(SEOUL_NOW);
        }
    }

    @Autowired
    private TradingSeasonRepository seasonRepository;
    @Autowired
    private TradeOrderRepository orderRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("보이는 시즌 번호 조회는 시작일이 가장 최근인 시즌 하나를, 공유 잠금 조회는 그 시즌을 돌려준다")
    void shareLockedLatestVisibleSeason() {
        TradingSeason older = TradingSeason.create("시즌 1", LocalDate.of(2026, 10, 19), 1L);
        older.changeStatus(TradingSeason.STATUS_RUNNING);
        older.changeStatus(TradingSeason.STATUS_SETTLING);
        TradingSeason running = TradingSeason.create("시즌 2", LocalDate.of(2026, 11, 2), 1L);
        running.changeStatus(TradingSeason.STATUS_RUNNING);
        TradingSeason ready = TradingSeason.create("시즌 3", LocalDate.of(2026, 11, 16), 1L);
        seasonRepository.saveAll(List.of(older, running, ready));
        entityManager.flush();
        entityManager.clear();

        List<Long> ids = seasonRepository.findSeasonIdsByStatusIn(
                List.of(TradingSeason.STATUS_RUNNING, TradingSeason.STATUS_HOLIDAY,
                        TradingSeason.STATUS_SETTLING, TradingSeason.STATUS_ENDED),
                PageRequest.of(0, 1));
        assertThat(ids).containsExactly(running.getTradingSeasonId());

        Optional<TradingSeason> found = seasonRepository.findByIdForShare(ids.get(0));

        assertThat(found).map(TradingSeason::getName).contains("시즌 2");
    }

    @Test
    @DisplayName("주문 생성 시각은 공통 시계(감시 기능)로 기록된다")
    void tradeOrderCreatedAt_usesAuditingClock() {
        TradeOrder saved = orderRepository.saveAndFlush(TradeOrder.createBuy(1L, 1L, 1, 2, 10_000, 1L));
        entityManager.clear();

        assertThat(orderRepository.findById(saved.getTradeOrderId()))
                .map(TradeOrder::getCreatedAt)
                .contains(SEOUL_NOW);
    }
}
