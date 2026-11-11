package com.wanted.moneybeetle.domain.trading.model.dao;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanted.moneybeetle.domain.trading.model.entity.SeasonParticipant;
import com.wanted.moneybeetle.domain.trading.model.entity.TradingSeason;
import com.wanted.moneybeetle.MoneyBeetleApplication;
import java.math.BigDecimal;
import java.time.LocalDate;
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
*   이월금을 가져올 이전 참가 기록 (#23, SP-63) : 참가한 순서가 아니라 시즌 시작일로 고르는지 확인한다
*  */
@EntityScan(basePackages = "com.wanted.moneybeetle.domain.trading.model.entity")
@EnableJpaRepositories(basePackages = "com.wanted.moneybeetle.domain.trading.model.dao")
@ContextConfiguration(classes = MoneyBeetleApplication.class)
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:season-participant-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SeasonParticipantRepositoryTest {

    private static final Long MEMBER_ID = 5L;

    @Autowired
    private TradingSeasonRepository seasonRepository;
    @Autowired
    private SeasonParticipantRepository participantRepository;

    private TradingSeason season(String name, LocalDate start) {
        return seasonRepository.saveAndFlush(TradingSeason.create(name, start, 1L));
    }

    private void participate(TradingSeason season, Long memberId, long carryoverOut) {
        SeasonParticipant p = SeasonParticipant.create(season.getTradingSeasonId(), memberId, 1_000_000L, 0L);
        p.settle(carryoverOut, BigDecimal.ZERO, carryoverOut);
        participantRepository.saveAndFlush(p);
    }

    @Test
    @DisplayName("나중 시즌에 먼저 참가했어도 앞선 시즌은 그보다 먼저 시작한 시즌의 이월금만 받는다")
    void picksEarlierStartedSeasonNotLatestJoin() {
        TradingSeason old = season("1기", LocalDate.of(2026, 9, 1));
        TradingSeason earlier = season("2기", LocalDate.of(2026, 10, 1));
        TradingSeason later = season("3기", LocalDate.of(2026, 10, 8));   // 2기와 기간이 겹친다
        participate(old, MEMBER_ID, 1_100_000L);
        participate(later, MEMBER_ID, 2_500_000L);                     // 3기에 먼저 참가
        participate(old, 99L, 9_000_000L);                             // 다른 회원

        assertThat(participantRepository.findPreviousParticipation(MEMBER_ID, earlier.getTradingSeasonId()))
                .get().extracting(SeasonParticipant::getCarryoverOut).isEqualTo(1_100_000L);
    }

    @Test
    @DisplayName("앞선 시즌 참가 기록이 여럿이면 가장 늦게 시작한 시즌을, 없으면 빈 결과를 돌려준다")
    void picksMostRecentEarlierSeason() {
        TradingSeason first = season("1기", LocalDate.of(2026, 8, 1));
        TradingSeason second = season("2기", LocalDate.of(2026, 9, 1));
        TradingSeason third = season("3기", LocalDate.of(2026, 10, 1));
        participate(second, MEMBER_ID, 1_620_000L);
        participate(first, MEMBER_ID, 1_200_000L);

        assertThat(participantRepository.findPreviousParticipation(MEMBER_ID, third.getTradingSeasonId()))
                .get().extracting(SeasonParticipant::getCarryoverOut).isEqualTo(1_620_000L);
        assertThat(participantRepository.findPreviousParticipation(MEMBER_ID, first.getTradingSeasonId())).isEmpty();
    }

    @Test
    @DisplayName("시작일이 같은 시즌이면 시즌 번호가 바로 앞선 참가 기록을 선택한다")
    void picksPreviousSeasonNumberWhenStartDatesMatch() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        TradingSeason first = season("1기", start);
        TradingSeason second = season("2기", start);
        TradingSeason third = season("3기", start);
        participate(first, MEMBER_ID, 1_100_000L);
        participate(second, MEMBER_ID, 1_620_000L);
        participate(third, MEMBER_ID, 2_500_000L);

        // 3기 기준으로는 2기가 직전 시즌이고, 2기 기준으로는 같은 날 시작한 3기를 제외해야 한다.
        assertThat(participantRepository.findPreviousParticipation(MEMBER_ID, third.getTradingSeasonId()))
                .get().extracting(SeasonParticipant::getCarryoverOut)
                .isEqualTo(1_620_000L);
        assertThat(participantRepository.findPreviousParticipation(MEMBER_ID, second.getTradingSeasonId()))
                .get().extracting(SeasonParticipant::getCarryoverOut)
                .isEqualTo(1_100_000L);
    }

}
