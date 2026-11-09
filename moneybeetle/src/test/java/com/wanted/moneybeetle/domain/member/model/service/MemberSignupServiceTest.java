package com.wanted.moneybeetle.domain.member.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.growth.model.dao.CashHistoryRepository;
import com.wanted.moneybeetle.domain.growth.model.dao.ExpHistoryRepository;
import com.wanted.moneybeetle.domain.growth.model.dao.LevelPolicyRepository;
import com.wanted.moneybeetle.domain.growth.model.dao.RewardRuleRepository;
import com.wanted.moneybeetle.domain.growth.model.service.GrowthService;
import com.wanted.moneybeetle.domain.growth.model.service.RewardService;
import com.wanted.moneybeetle.domain.housing.model.dao.HousingApplicationRepository;
import com.wanted.moneybeetle.domain.housing.model.dao.HousingComplexRepository;
import com.wanted.moneybeetle.domain.housing.model.dao.SubscriptionAccountRepository;
import com.wanted.moneybeetle.domain.housing.model.dao.SubscriptionDepositRepository;
import com.wanted.moneybeetle.domain.housing.model.entity.SubscriptionAccount;
import com.wanted.moneybeetle.domain.housing.model.service.HousingService;
import com.wanted.moneybeetle.domain.member.model.dao.InstructorProfileRepository;
import com.wanted.moneybeetle.domain.member.model.dao.MemberRepository;
import com.wanted.moneybeetle.domain.member.model.dao.MemberTermsAgreementRepository;
import com.wanted.moneybeetle.domain.member.model.dao.TermsRepository;
import com.wanted.moneybeetle.domain.member.model.dto.SignupDTO;
import com.wanted.moneybeetle.domain.member.model.dto.SignupResultDTO;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.domain.member.model.entity.Terms;
import com.wanted.moneybeetle.domain.operation.model.service.ForbiddenWordService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

/* comment.
*   담당 : DBA ② (회원·인증)
*   회원가입 + 청약통장 자동 개설 (이슈 #5) - 실제 서비스(MemberService → HousingService → RewardService → ...)를 스프링으로 이어 붙이고
*   저장소만 가짜로 둔다. DB 없이 돈다.
*   - 서비스끼리 서로 물리지 않고 서버가 뜨는지(순환 참조), 회원 저장 뒤 같은 트랜잭션에서 통장이 열리는지,
*     통장 개설이 실패하면 트랜잭션이 되돌려지는지를 확인한다.
*  */
@SpringBootTest(classes = {MemberSignupServiceTest.TestApp.class, MemberSignupService.class, MemberService.class,
        HousingService.class, RewardService.class, GrowthService.class})
class MemberSignupServiceTest {

    @Configuration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class})
    static class TestApp {
        @Bean
        PlatformTransactionManager transactionManager() {
            // 트랜잭션이 열리고 닫히는지만 확인하려고 가짜 관리자를 쓴다 (커밋·롤백 호출을 세어 본다)
            PlatformTransactionManager manager = org.mockito.Mockito.mock(PlatformTransactionManager.class);
            when(manager.getTransaction(any())).thenReturn(org.mockito.Mockito.mock(TransactionStatus.class));
            return manager;
        }

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-09T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    @Autowired
    private MemberSignupService signupService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    /** #171(레벨업 해금 알림)이 병합되면 RewardService 가 알림 서비스를 쓴다. 이 테스트는 알림을 보지 않아 가짜로 둔다 */
    @MockitoBean
    private com.wanted.moneybeetle.domain.notification.model.service.NotificationService notificationService;

    @MockitoBean
    private MemberRepository memberRepository;
    @MockitoBean
    private TermsRepository termsRepository;
    @MockitoBean
    private MemberTermsAgreementRepository memberTermsAgreementRepository;
    @MockitoBean
    private InstructorProfileRepository instructorProfileRepository;
    @MockitoBean
    private PasswordEncoder passwordEncoder;
    @MockitoBean
    private ForbiddenWordService forbiddenWordService;
    @MockitoBean
    private RewardRuleRepository rewardRuleRepository;
    @MockitoBean
    private CashHistoryRepository cashHistoryRepository;
    @MockitoBean
    private ExpHistoryRepository expHistoryRepository;
    @MockitoBean
    private LevelPolicyRepository levelPolicyRepository;
    @MockitoBean
    private HousingComplexRepository housingComplexRepository;
    @MockitoBean
    private HousingApplicationRepository housingApplicationRepository;
    @MockitoBean
    private SubscriptionAccountRepository subscriptionAccountRepository;
    @MockitoBean
    private SubscriptionDepositRepository subscriptionDepositRepository;

    private SignupDTO request;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.clearInvocations(transactionManager);
        Terms required = Terms.create("서비스 이용약관", "내용", true, "1.0", LocalDateTime.of(2026, 1, 1, 0, 0));
        ReflectionTestUtils.setField(required, "termsId", 1L);
        when(termsRepository.findByTermsIdIn(any())).thenReturn(List.of(required));
        when(termsRepository.findByRequiredTrue()).thenReturn(List.of(required));
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(memberRepository.saveAndFlush(any(Member.class))).thenAnswer(invocation -> {
            Member saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "memberId", 42L);
            return saved;
        });
        when(subscriptionAccountRepository.findByMemberId(42L)).thenReturn(Optional.empty());

        request = new SignupDTO("minji@gmail.com", "beetle1234", "beetle1234", "민지",
                LocalDate.of(1999, 4, 12), "FEMALE", List.of(1L));
    }

    @Test
    @DisplayName("서비스들이 서로 물리지 않고 한 묶음으로 만들어진다 (순환 참조 없음)")
    void contextStarts() {
        assertThat(signupService).isNotNull();
    }

    @Test
    @DisplayName("가입하면 회원을 저장한 뒤 그 회원의 청약통장을 같은 트랜잭션에서 만든다")
    void opensAccountAfterSignup() {
        SignupResultDTO result = signupService.signup(request);

        assertThat(result.getMemberId()).isEqualTo(42L);
        InOrder order = inOrder(memberRepository, subscriptionAccountRepository);
        order.verify(memberRepository).saveAndFlush(any(Member.class));
        ArgumentCaptor<SubscriptionAccount> account = ArgumentCaptor.forClass(SubscriptionAccount.class);
        order.verify(subscriptionAccountRepository).save(account.capture());
        assertThat(account.getValue().getMemberId()).isEqualTo(42L);
        verify(transactionManager, never()).rollback(any());   // 가짜 관리자는 겹친 트랜잭션도 따로 센다. 그래서 되돌림이 없는지만 본다
    }

    @Test
    @DisplayName("청약통장 개설이 실패하면 가입도 되돌려진다 (통장 없는 회원이 남지 않는다)")
    void rollsBackSignupWhenAccountFails() {
        when(subscriptionAccountRepository.save(any(SubscriptionAccount.class)))
                .thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> signupService.signup(request)).isInstanceOf(IllegalStateException.class);

        // 가짜 관리자는 바깥·안쪽 트랜잭션을 구분하지 못해 "어딘가에서 되돌렸다"까지만 안다. 가입이 실제로 취소되는지(회원이 DB 에 남지 않는지)는
        // MemberSignupRollbackTest 가 H2 실제 DB 로 확인한다.
        verify(transactionManager, atLeastOnce()).rollback(any());
    }

    @Test
    @DisplayName("가입 검증에 실패하면(중복 이메일 등) 통장은 만들지 않는다")
    void noAccountWhenSignupRejected() {
        when(memberRepository.existsByEmail("minji@gmail.com")).thenReturn(true);

        assertThatThrownBy(() -> signupService.signup(request)).isInstanceOf(RuntimeException.class);

        verify(subscriptionAccountRepository, never()).save(any(SubscriptionAccount.class));
    }
}
