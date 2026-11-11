package com.wanted.moneybeetle.domain.trading.model.dao;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanted.moneybeetle.MoneyBeetleApplication;
import com.wanted.moneybeetle.domain.trading.model.entity.TradingSeason;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

/* comment.
*   담당 : PM (모의투자)
*   시즌 생성 전 전체 잠금 조회 (#207) : 상태와 상관없이 모든 시즌을 시작일 최신순으로 읽는 쿼리가 실제로 실행되는지 확인한다
*  */
@EntityScan(basePackages = "com.wanted.moneybeetle.domain.trading.model.entity")
@EnableJpaRepositories(basePackages = "com.wanted.moneybeetle.domain.trading.model.dao")
@ContextConfiguration(classes = MoneyBeetleApplication.class)
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:trading-season-repo-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TradingSeasonRepositoryTest {

    @Autowired
    private TradingSeasonRepository seasonRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("전체 잠금 조회는 상태와 상관없이 모든 시즌을 시작일 최신순으로 돌려준다")
    void findAllForUpdate_returnsEverySeasonNewestFirst() {
        TradingSeason ended = TradingSeason.create("시즌 1", LocalDate.of(2026, 10, 19), 1L);
        ended.changeStatus(TradingSeason.STATUS_RUNNING);
        ended.changeStatus(TradingSeason.STATUS_SETTLING);
        ended.changeStatus(TradingSeason.STATUS_ENDED);
        TradingSeason running = TradingSeason.create("시즌 2", LocalDate.of(2026, 11, 2), 1L);
        running.changeStatus(TradingSeason.STATUS_RUNNING);
        TradingSeason ready = TradingSeason.create("시즌 3", LocalDate.of(2026, 11, 16), 1L);
        seasonRepository.saveAll(List.of(ended, running, ready));
        entityManager.flush();
        entityManager.clear();

        assertThat(seasonRepository.findAllForUpdate())
                .extracting(TradingSeason::getName)
                .containsExactly("시즌 3", "시즌 2", "시즌 1");
    }

    @Test
    @DisplayName("시즌이 하나도 없으면 빈 목록이다")
    void findAllForUpdate_emptyWhenNoSeason() {
        assertThat(seasonRepository.findAllForUpdate()).isEmpty();
    }
}
