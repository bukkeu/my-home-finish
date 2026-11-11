package com.wanted.moneybeetle.domain.member.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wanted.moneybeetle.domain.housing.model.dao.SubscriptionAccountRepository;
import com.wanted.moneybeetle.domain.member.model.dao.MemberRepository;
import com.wanted.moneybeetle.domain.member.model.dao.MemberTermsAgreementRepository;
import com.wanted.moneybeetle.domain.member.model.dao.TermsRepository;
import com.wanted.moneybeetle.domain.member.model.dto.SignupDTO;
import com.wanted.moneybeetle.domain.member.model.entity.Terms;
import com.wanted.moneybeetle.MoneyBeetleApplication;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/* comment.
*   담당 : DBA ② (회원·인증)
*   회원가입 + 청약통장 개설이 실제 DB(H2)에서 한 묶음으로 저장·취소되는지 확인한다 (이슈 #5, CodeRabbit 제안).
*   - 실제 서비스(MemberSignupService → MemberService, HousingService → RewardService …)와 실제 저장소를 쓰고, 테스트는 트랜잭션으로 감싸지 않는다.
*   - 통장 저장 실패는 청약통장 테이블을 지워서 만든다. 가입이 취소되면 같은 이메일의 회원이 DB 에 남지 않아야 한다.
*   - 테스트마다 새 컨텍스트(새 DB)를 쓴다. 한 테스트가 테이블을 지워도 다른 테스트에 영향이 없다.
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
        "spring.datasource.url=jdbc:h2:mem:signup-rollback;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MemberSignupService.class, MemberService.class,
        com.wanted.moneybeetle.domain.housing.model.service.HousingService.class,
        com.wanted.moneybeetle.domain.growth.model.service.RewardService.class,
        com.wanted.moneybeetle.domain.growth.model.service.GrowthService.class,
        com.wanted.moneybeetle.domain.operation.model.service.ForbiddenWordService.class,
        MemberSignupRollbackTest.TestBeans.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)   // 테스트가 감싸는 트랜잭션 없이, 서비스가 여는 트랜잭션이 실제로 커밋·롤백되게 한다
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MemberSignupRollbackTest {

    @TestConfiguration
    @EnableJpaAuditing   // created_at·updated_at 을 채워 주는 감시 기능 (운영은 global.config.JpaConfig 가 켠다)
    static class TestBeans {
        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(4);   // 테스트라서 빠르게
        }

        @Bean
        Clock clock() {
            return Clock.system(ZoneId.of("Asia/Seoul"));
        }
    }

    /** #171(레벨업 해금 알림)이 병합되면 RewardService 가 알림 서비스를 쓴다. 이 테스트는 알림을 보지 않아 가짜로 둔다 */
    @MockitoBean
    private com.wanted.moneybeetle.domain.notification.model.service.NotificationService notificationService;

    /** 본인인증 확인은 IdentityVerificationServiceTest 가 따로 본다. 여기서는 인증을 마친 것으로 두고 가입·통장의 한 묶음만 본다 */
    @MockitoBean
    private SignupIdentityVerifier identityVerificationService;

    private final org.springframework.mock.web.MockHttpSession session = new org.springframework.mock.web.MockHttpSession();

    @Autowired
    private MemberSignupService signupService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private MemberTermsAgreementRepository agreementRepository;
    @Autowired
    private TermsRepository termsRepository;
    @Autowired
    private SubscriptionAccountRepository accountRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private Clock clock;

    private SignupDTO request;

    @BeforeEach
    void setUp() {
        Long termsId = new TransactionTemplate(transactionManager).execute(status -> termsRepository.save(
                Terms.create("서비스 이용약관", "내용", true, "1.0", LocalDateTime.now(clock))).getTermsId());
        request = new SignupDTO("minji@gmail.com", "beetle1234", "beetle1234", "민지",
                LocalDate.of(1999, 4, 12), "FEMALE", List.of(termsId));
    }

    @Test
    @DisplayName("가입하면 회원과 청약통장이 함께 저장된다")
    void savesMemberAndAccount() {
        Long memberId = signupService.signup(request, session).getMemberId();

        assertThat(memberRepository.existsByEmail("minji@gmail.com")).isTrue();
        assertThat(accountRepository.findByMemberId(memberId)).isPresent();
        // 본인인증을 마친 가입이라 그 결과가 회원에 저장되고, 가입이 끝나면 인증 표시를 지운다
        assertThat(memberRepository.findById(memberId).orElseThrow().isIdentityVerified()).isTrue();
        org.mockito.Mockito.verify(identityVerificationService).requireVerified("minji@gmail.com", session);
        org.mockito.Mockito.verify(identityVerificationService).clear(session);
    }

    @Test
    @DisplayName("청약통장 저장이 실패하면 가입이 취소되어 회원도 약관 동의도 DB 에 남지 않는다")
    void accountFailureRollsBackMember() {
        jdbcTemplate.execute("drop table subscription_account");   // 통장을 저장할 수 없게 만든다

        // 아무 예외나 받지 않는다: 나이·약관 검증 같은 가입 규칙(BusinessException)에서 먼저 실패해도 통과하는 일이 없도록,
        // 청약통장 테이블이 없어서 생긴 DB 오류인지 확인한다. 같은 요청이 savesMemberAndAccount 에서는 가입에 성공한다.
        assertThatThrownBy(() -> signupService.signup(request, session))
                .isInstanceOf(DataAccessException.class)
                .hasStackTraceContaining("SUBSCRIPTION_ACCOUNT");

        assertThat(memberRepository.existsByEmail("minji@gmail.com")).isFalse();
        assertThat(memberRepository.existsByNickname("민지")).isFalse();
        assertThat(agreementRepository.count()).isZero();
    }
}
