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
*   모의투자 홈의 지난 시즌 (#310) : 랭킹을 공개한 시즌만 고르는 쿼리가 실제 DB 에서 맞게 고르는지 확인한다
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

    /** 정산하고 랭킹을 공개해 끝난 시즌 */
    private static TradingSeason published(String name, LocalDate start) {
        TradingSeason season = TradingSeason.create(name, start, 1L);
        season.changeStatus(TradingSeason.STATUS_RUNNING);
        season.changeStatus(TradingSeason.STATUS_SETTLING);
        season.completeSettlement(start.plusDays(14).atTime(9, 0));
        season.publishRanking(start.plusDays(14).atTime(10, 0));
        return season;
    }

    @Test
    @DisplayName("지난 시즌(#310)은 먼저 시작해 랭킹을 공개한 시즌 중 가장 최근 것이고, 공개 없이 종료만 한 시즌은 건너뛴다")
    void previousPublishedSeason_skipsEndedWithoutRanking() {
        // 기준일보다 앞서 공개한 시즌이 둘 : 시작일이 더 늦은 시즌 5 를 골라야 한다 (정렬이 뒤집히면 시즌 4 가 나온다)
        TradingSeason olderPublished = published("시즌 4", LocalDate.of(2026, 9, 21));
        TradingSeason oldest = published("시즌 5", LocalDate.of(2026, 10, 5));
        TradingSeason endedOnly = TradingSeason.create("시즌 6", LocalDate.of(2026, 10, 19), 1L);
        endedOnly.changeStatus(TradingSeason.STATUS_RUNNING);
        endedOnly.changeStatus(TradingSeason.STATUS_SETTLING);
        endedOnly.changeStatus(TradingSeason.STATUS_ENDED);   // 랭킹을 공개하지 않고 상태만 종료
        TradingSeason current = TradingSeason.create("시즌 7", LocalDate.of(2026, 11, 2), 1L);
        current.changeStatus(TradingSeason.STATUS_RUNNING);
        TradingSeason later = published("시즌 8", LocalDate.of(2026, 11, 16));   // 더 늦게 시작한 시즌은 지난 시즌이 아니다
        seasonRepository.saveAll(List.of(olderPublished, oldest, endedOnly, current, later));
        entityManager.flush();
        entityManager.clear();

        assertThat(seasonRepository
                .findFirstByStartDateBeforeAndRankingPublishedAtIsNotNullOrderByStartDateDesc(LocalDate.of(2026, 11, 2)))
                .map(TradingSeason::getName)
                .contains("시즌 5");
        assertThat(seasonRepository
                .findFirstByStartDateBeforeAndRankingPublishedAtIsNotNullOrderByStartDateDesc(LocalDate.of(2026, 10, 5)))
                .map(TradingSeason::getName)
                .contains("시즌 4");   // 기준일 당일에 시작한 시즌(시즌 5)은 '먼저 시작한' 시즌이 아니다
        assertThat(seasonRepository
                .findFirstByStartDateBeforeAndRankingPublishedAtIsNotNullOrderByStartDateDesc(LocalDate.of(2026, 9, 21)))
                .isEmpty();
    }
}
