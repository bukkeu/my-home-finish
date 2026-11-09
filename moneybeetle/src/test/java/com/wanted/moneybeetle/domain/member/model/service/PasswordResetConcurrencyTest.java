package com.wanted.moneybeetle.domain.member.model.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.domain.member.model.dao.MemberRepository;
import com.wanted.moneybeetle.domain.member.model.dao.PasswordResetTokenRepository;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.domain.member.model.entity.PasswordResetToken;
import com.wanted.moneybeetle.global.config.MoneyBeetleApplication;
import com.wanted.moneybeetle.global.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/* comment.
*   담당 : DBA ② (회원·인증)
*   같은 재설정 토큰으로 동시에 두 번 재설정해도 한 번만 되는지 H2 로 확인한다 (#8, CodeRabbit 제안).
*   - 두 스레드가 CountDownLatch 로 동시에 AccountService.resetPassword 를 부르고, 토큰 행을 잠그는 findByTokenHashForUpdate 때문에
*     정확히 한 요청만 성공하고 나머지는 TOKEN_USED 로 거부돼야 한다.
*   - H2(MODE=MySQL)의 행 잠금으로 확인한다. 실제 MySQL(InnoDB)과 잠금 구현은 다르지만 같은 SELECT ... FOR UPDATE 의미를 쓴다.
*  */
@EntityScan(basePackages = "com.wanted.moneybeetle.domain.member.model.entity")
@EnableJpaRepositories(basePackages = "com.wanted.moneybeetle.domain.member.model.dao")
@ContextConfiguration(classes = MoneyBeetleApplication.class)
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:password-reset-concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AccountService.class, PasswordResetConcurrencyTest.TestBeans.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)   // 테스트가 감싸는 트랜잭션 없이, 스레드마다 서비스의 트랜잭션이 따로 열리게 한다
class PasswordResetConcurrencyTest {

    @TestConfiguration
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

    @Autowired
    private AccountService accountService;
    /** 서비스가 시간을 판단하는 시계와 같은 시계로 토큰을 만든다. 시스템 기본 시간대(CI 는 UTC)로 만들면 서울 시계와 9시간 어긋나 토큰이 곧바로 만료로 보인다 */
    @Autowired
    private Clock clock;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private PasswordResetTokenRepository tokenRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("같은 토큰으로 동시에 두 번 재설정하면 한 번만 성공하고 나머지는 TOKEN_USED")
    void onlyOneResetSucceeds() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        String hash = "concurrent-token-hash";
        tx.executeWithoutResult(status -> {
            Member member = memberRepository.save(Member.signup("race@gmail.com", "old-hash", "경쟁",
                    LocalDate.of(1999, 4, 12), "FEMALE", LocalDate.now(clock)));
            tokenRepository.save(PasswordResetToken.issue(member.getMemberId(), hash, LocalDateTime.now(clock)));
        });

        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    accountService.resetPassword(hash, "beetle5678", "beetle5678");
                    return "OK";
                } catch (BusinessException e) {
                    return e.getErrorCode().getCode();
                }
            }));
        }
        ready.await();
        go.countDown();

        List<String> outcomes = new ArrayList<>();
        for (Future<String> result : results) {
            outcomes.add(result.get(20, TimeUnit.SECONDS));
        }
        pool.shutdownNow();

        assertThat(outcomes).containsExactlyInAnyOrder("OK", MemberErrorCode.TOKEN_USED.getCode());
        assertThat(tokenRepository.findAll()).filteredOn(token -> hash.equals(token.getTokenHash()))
                .singleElement().satisfies(token -> assertThat(token.isUsed()).isTrue());
    }
}
