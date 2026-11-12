package com.wanted.moneybeetle.domain.inquiry.model.dao;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanted.moneybeetle.MoneyBeetleApplication;
import com.wanted.moneybeetle.domain.inquiry.model.entity.Inquiry;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
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
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

/* comment.
*   담당 : PM 이재성 (#370 리뷰, 신아림 담당 inquiry 도메인에서 나눠 받은 작업)
*   답글 작성·삭제가 쓰는 잠금 조회(SELECT ... FOR UPDATE)가 실제로 실행되고, 삭제된 문의는 돌려주지 않는지 확인한다
*  */
@EntityScan(basePackages = "com.wanted.moneybeetle.domain.inquiry.model.entity")
@EnableJpaRepositories(basePackages = "com.wanted.moneybeetle.domain.inquiry.model.dao")
@ContextConfiguration(classes = MoneyBeetleApplication.class)
@Import(InquiryLockRepositoryTest.AuditingConfig.class)
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:inquiry-lock-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InquiryLockRepositoryTest {

    @TestConfiguration
    @EnableJpaAuditing(dateTimeProviderRef = "testDateTimeProvider")
    static class AuditingConfig {
        @Bean
        DateTimeProvider testDateTimeProvider() {
            return () -> Optional.of(LocalDateTime.of(2026, 11, 12, 10, 0));
        }
    }

    @Autowired
    private InquiryRepository inquiryRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("잠금 조회는 살아 있는 문의만 돌려준다")
    void findActiveByIdForUpdate() {
        Inquiry active = inquiryRepository.save(Inquiry.create(8L, 15L, null, "질문", "내용"));
        Inquiry deleted = inquiryRepository.save(Inquiry.create(8L, 15L, null, "지운 질문", "내용"));
        deleted.delete(LocalDateTime.of(2026, 11, 12, 9, 0));
        entityManager.flush();
        entityManager.clear();

        assertThat(inquiryRepository.findActiveByIdForUpdate(active.getInquiryId()))
                .get().extracting(Inquiry::getTitle).isEqualTo("질문");
        assertThat(inquiryRepository.findActiveByIdForUpdate(deleted.getInquiryId())).isEmpty();
    }
}
