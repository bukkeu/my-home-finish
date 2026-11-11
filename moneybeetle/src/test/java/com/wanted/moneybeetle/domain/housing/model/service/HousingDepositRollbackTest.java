package com.wanted.moneybeetle.domain.housing.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wanted.moneybeetle.MoneyBeetleApplication;
import com.wanted.moneybeetle.domain.growth.exception.GrowthErrorCode;
import com.wanted.moneybeetle.domain.growth.model.dao.CashHistoryRepository;
import com.wanted.moneybeetle.domain.housing.model.dao.SubscriptionDepositRepository;
import com.wanted.moneybeetle.domain.housing.model.dto.AccountDTO;
import com.wanted.moneybeetle.domain.member.model.dao.MemberRepository;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.notification.model.service.NotificationService;
import com.wanted.moneybeetle.domain.operation.model.service.SystemSettingService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.security.UnlockLevelProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/* comment.
*   담당 : SCM (모의 청약)
*   #291 (PR #317 CodeRabbit 제안) 청약통장 납입이 실제 DB(H2)에서 한 묶음으로 저장·취소되는지 확인한다.
*   - 납입 순서는 통장 반영 → 납입 내역 저장 → 캐시 차감이라, 캐시가 부족하면 앞의 두 단계가 트랜잭션 롤백으로 함께 취소돼야 한다
*   - 실제 서비스(HousingService → RewardService → MemberService)와 실제 저장소를 쓰고, 테스트는 트랜잭션으로 감싸지 않는다
*     (MemberSignupRollbackTest 와 같은 방식). 실패한 호출이 끝난 뒤 통장·납입 내역·캐시·캐시 사용 기록을 다시 읽어 그대로인지 본다
*   - 테스트마다 새 컨텍스트(새 DB)를 쓴다. 오늘은 2026-11-05 (서울) 로 고정
*  */
@EntityScan(basePackages = {
        "com.wanted.moneybeetle.domain.member.model.entity",
        "com.wanted.moneybeetle.domain.housing.model.entity",
        "com.wanted.moneybeetle.domain.growth.model.entity",
        "com.wanted.moneybeetle.domain.operation.model.entity"})
@EnableJpaRepositories(basePackages = {
        "com.wanted.moneybeetle.domain.member.model.dao",
        "com.wanted.moneybeetle.domain.housing.model.dao",
        "com.wanted.moneybeetle.domain.growth.model.dao",
        "com.wanted.moneybeetle.domain.operation.model.dao"})
@ContextConfiguration(classes = MoneyBeetleApplication.class)
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:housing-deposit-rollback;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({HousingService.class, MemberService.class,
        com.wanted.moneybeetle.domain.growth.model.service.RewardService.class,
        com.wanted.moneybeetle.domain.growth.model.service.GrowthService.class,
        UnlockLevelProvider.class,
        com.wanted.moneybeetle.domain.operation.model.service.ForbiddenWordService.class,
        HousingDepositRollbackTest.TestBeans.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)   // 테스트가 감싸는 트랜잭션 없이, 서비스가 여는 트랜잭션이 실제로 커밋·롤백되게 한다
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class HousingDepositRollbackTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

    @TestConfiguration
    @EnableJpaAuditing   // created_at·updated_at 을 채워 주는 감시 기능 (운영은 global.config.JpaConfig 가 켠다)
    static class TestBeans {
        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(4);
        }

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-05T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    /** 해금 레벨 운영 설정은 이 테스트가 보지 않는다 (설정이 없으면 기본값) */
    @MockitoBean
    private SystemSettingService systemSettingService;
    /** 캐시 사용은 알림을 보내지 않지만 RewardService 가 알림 서비스를 쓴다. 이 테스트는 알림을 보지 않아 가짜로 둔다 */
    @MockitoBean
    private NotificationService notificationService;

    @Autowired
    private HousingService housingService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private SubscriptionDepositRepository depositRepository;
    @Autowired
    private CashHistoryRepository cashHistoryRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    /** 캐시 cash 를 가진 회원과 빈 청약통장을 커밋해 둔다 */
    private Long givenMemberWithCash(long cash) {
        Long memberId = new TransactionTemplate(transactionManager).execute(status -> {
            Member member = Member.signup("minji@gmail.com", "hash", "민지", LocalDate.of(1999, 4, 12), "FEMALE", TODAY);
            member.addReward(cash, 0);
            return memberRepository.save(member).getMemberId();
        });
        housingService.openAccount(memberId);
        return memberId;
    }

    @Test
    void 캐시가_충분하면_통장_납입내역_캐시_사용기록이_함께_저장된다() {
        Long memberId = givenMemberWithCash(500_000L);

        housingService.deposit(memberId, 300_000L);

        AccountDTO after = housingService.getAccount(memberId);
        assertThat(after.getBalance()).isEqualTo(300_000L);
        assertThat(after.getRecognizedAmount()).isEqualTo(250_000L);
        assertThat(after.getPaidCount()).isEqualTo(1);
        assertThat(after.getLastPaidDate()).isEqualTo(TODAY);
        assertThat(after.getCash()).isEqualTo(200_000L);
        assertThat(depositRepository.count()).isEqualTo(1);
        assertThat(cashHistoryRepository.count()).isEqualTo(1);
    }

    @Test
    void 캐시가_부족하면_통장_반영과_납입내역이_함께_취소되어_아무것도_남지_않는다() {
        Long memberId = givenMemberWithCash(100_000L);
        AccountDTO before = housingService.getAccount(memberId);

        assertThatThrownBy(() -> housingService.deposit(memberId, 300_000L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(GrowthErrorCode.NOT_ENOUGH_CASH);

        // 실패한 서비스 트랜잭션이 끝난 뒤 DB 에서 다시 읽는다
        AccountDTO after = housingService.getAccount(memberId);
        assertThat(after.getBalance()).isEqualTo(before.getBalance());
        assertThat(after.getRecognizedAmount()).isEqualTo(before.getRecognizedAmount());
        assertThat(after.getPaidCount()).isEqualTo(before.getPaidCount());
        assertThat(after.getLastPaidDate()).isEqualTo(before.getLastPaidDate());
        assertThat(after.isPaidToday()).isFalse();                 // 같은 날 다시 납입할 수 있다
        assertThat(after.getCash()).isEqualTo(before.getCash());
        assertThat(depositRepository.count()).isZero();            // 납입 내역이 남지 않는다
        assertThat(cashHistoryRepository.count()).isZero();        // 캐시 사용 기록도 남지 않는다
    }
}
