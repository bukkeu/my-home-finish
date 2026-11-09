package com.wanted.moneybeetle.domain.term.model.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wanted.moneybeetle.domain.term.model.entity.EconomicTerm;
import com.wanted.moneybeetle.domain.term.model.entity.TermSchedule;
import com.wanted.moneybeetle.global.config.MoneyBeetleApplication;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

@EntityScan(basePackages = "com.wanted.moneybeetle.domain.term.model.entity")
@EnableJpaRepositories(basePackages = "com.wanted.moneybeetle.domain.term.model.dao")
@ContextConfiguration(classes = MoneyBeetleApplication.class)
@Import(TermRepositoryTest.AuditingConfig.class)
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:term-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TermRepositoryTest {

    /** 생성·수정 시각을 자동으로 채우는 감시 기능(JpaConfig 와 같은 설정). JpaConfig 를 그대로 가져오면 모든 도메인을 스캔해서 용어 도메인만 켠다 */
    @TestConfiguration
    @EnableJpaAuditing(dateTimeProviderRef = "testDateTimeProvider")
    static class AuditingConfig {
        @Bean
        DateTimeProvider testDateTimeProvider() {
            return () -> Optional.of(LocalDateTime.now());
        }
    }

    @Autowired
    private EconomicTermRepository termRepository;
    @Autowired
    private TermScheduleRepository scheduleRepository;
    @Autowired
    private EntityManager entityManager;

    private EconomicTerm saveTerm(String term, String description) {
        return termRepository.saveAndFlush(EconomicTerm.create(term, description));
    }

    @Test
    @DisplayName("사용 중인 용어만 번호(등록) 순서로 돌려준다")
    void findsActiveTermsInOrder() {
        EconomicTerm a = saveTerm("복리", "이자에 이자");
        EconomicTerm off = saveTerm("단리", "원금에만 이자");
        off.deactivate();
        EconomicTerm c = saveTerm("인플레이션", "물가 상승");
        termRepository.flush();

        assertThat(termRepository.findByActiveTrueOrderByEconomicTermIdAsc())
                .extracting(EconomicTerm::getEconomicTermId).containsExactly(a.getEconomicTermId(), c.getEconomicTermId());
    }

    @Test
    @DisplayName("키워드는 용어와 뜻에서 찾고, 키워드가 없으면 전체를 등록 순으로 준다")
    void searchesByKeyword() {
        saveTerm("복리", "이자에 이자가 붙는 방식");
        saveTerm("인플레이션", "물가가 오르는 현상");
        saveTerm("단리", "원금에만 이자가 붙는 방식");
        var page = PageRequest.of(0, 50);

        assertThat(termRepository.search(null, page).getContent()).extracting(EconomicTerm::getTerm)
                .containsExactly("복리", "인플레이션", "단리");
        assertThat(termRepository.search("이자", page).getContent()).extracting(EconomicTerm::getTerm)
                .containsExactly("복리", "단리");
        assertThat(termRepository.search("물가", page).getContent()).extracting(EconomicTerm::getTerm)
                .containsExactly("인플레이션");
        assertThat(termRepository.search("없는말", page).getContent()).isEmpty();
    }

    @Test
    @DisplayName("용어 중복 확인은 자기 자신을 뺀다")
    void duplicateCheckExcludesSelf() {
        EconomicTerm term = saveTerm("복리", "뜻");

        assertThat(termRepository.existsByTerm("복리")).isTrue();
        assertThat(termRepository.existsByTermAndEconomicTermIdNot("복리", term.getEconomicTermId())).isFalse();
        assertThat(termRepository.existsByTermAndEconomicTermIdNot("복리", term.getEconomicTermId() + 1)).isTrue();
    }

    @Test
    @DisplayName("같은 용어를 두 번 저장하면 DB 제약에 걸린다")
    void termIsUnique() {
        saveTerm("복리", "뜻");

        assertThatThrownBy(() -> saveTerm("복리", "다른 뜻")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("번호로 고른 용어 중 사용 중인 것만 돌려준다")
    void findsActiveByIds() {
        EconomicTerm a = saveTerm("가", "뜻");
        EconomicTerm off = saveTerm("나", "뜻");
        off.deactivate();
        termRepository.flush();

        assertThat(termRepository.findByEconomicTermIdInAndActiveTrue(List.of(a.getEconomicTermId(), off.getEconomicTermId())))
                .extracting(EconomicTerm::getEconomicTermId).containsExactly(a.getEconomicTermId());
    }

    @Test
    @DisplayName("일정은 날짜·순서 순으로 읽고, 월 범위와 노출 이력 확인이 된다")
    void scheduleQueries() {
        EconomicTerm t1 = saveTerm("가", "뜻");
        EconomicTerm t2 = saveTerm("나", "뜻");
        LocalDate d1 = LocalDate.of(2026, 11, 5);
        LocalDate d2 = LocalDate.of(2026, 12, 1);
        scheduleRepository.saveAndFlush(TermSchedule.create(d1, t2.getEconomicTermId(), 2, false));
        scheduleRepository.saveAndFlush(TermSchedule.create(d1, t1.getEconomicTermId(), 1, false));
        scheduleRepository.saveAndFlush(TermSchedule.create(d2, t1.getEconomicTermId(), 1, true));

        assertThat(scheduleRepository.findByShowDateOrderBySortOrderAsc(d1))
                .extracting(TermSchedule::getEconomicTermId).containsExactly(t1.getEconomicTermId(), t2.getEconomicTermId());
        assertThat(scheduleRepository.findByShowDateBetweenOrderByShowDateAscSortOrderAsc(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))).hasSize(2);
        assertThat(scheduleRepository.existsByEconomicTermId(t1.getEconomicTermId())).isTrue();
        assertThat(scheduleRepository.existsByEconomicTermId(t1.getEconomicTermId() + 100)).isFalse();
    }

    @Test
    @DisplayName("같은 날짜의 일정을 지우고 바로 새 일정을 넣을 수 있고, 같은 날짜·순서나 같은 용어는 DB 제약에 걸린다")
    void replaceAndUniqueConstraints() {
        EconomicTerm t1 = saveTerm("가", "뜻");
        EconomicTerm t2 = saveTerm("나", "뜻");
        LocalDate day = LocalDate.of(2026, 11, 9);
        scheduleRepository.saveAndFlush(TermSchedule.create(day, t1.getEconomicTermId(), 1, true));

        assertThat(scheduleRepository.deleteByShowDate(day)).isEqualTo(1);
        scheduleRepository.saveAndFlush(TermSchedule.create(day, t1.getEconomicTermId(), 1, false));   // 지운 자리에 같은 값
        entityManager.clear();
        assertThat(scheduleRepository.findByShowDateOrderBySortOrderAsc(day)).hasSize(1);

        assertThatThrownBy(() -> scheduleRepository.saveAndFlush(
                TermSchedule.create(day, t2.getEconomicTermId(), 1, false)))   // 같은 날짜·같은 순서
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
