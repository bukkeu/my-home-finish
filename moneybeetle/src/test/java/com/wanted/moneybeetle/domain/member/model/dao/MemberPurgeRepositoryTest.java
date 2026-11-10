package com.wanted.moneybeetle.domain.member.model.dao;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.domain.member.model.entity.PasswordResetToken;
import com.wanted.moneybeetle.global.config.MoneyBeetleApplication;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

@EntityScan(basePackages = "com.wanted.moneybeetle.domain.member.model.entity")
@EnableJpaRepositories(basePackages = "com.wanted.moneybeetle.domain.member.model.dao")
@ContextConfiguration(classes = MoneyBeetleApplication.class)
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:member-purge-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MemberPurgeRepositoryTest {

    private static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 10, 10, 3, 0);

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    private Member member(String email, String nickname, LocalDateTime withdrawnAt) {
        Member member = Member.signup(email, "hash", nickname, LocalDate.of(1999, 4, 12), "FEMALE",
                LocalDate.of(2026, 9, 1));
        if (withdrawnAt != null) {
            member.withdraw(withdrawnAt);
        }
        return memberRepository.saveAndFlush(member);
    }

    @Test
    @DisplayName("탈퇴한 지 기준 시각 이전이고 개인정보가 남은 회원만 번호 순으로 돌려준다")
    void findsOnlyDueWithdrawnMembers() {
        Member old1 = member("old1@gmail.com", "오래전1", CUTOFF.minusDays(1));
        member("recent@gmail.com", "최근탈퇴", CUTOFF.plusDays(1));      // 30일이 안 지남
        member("active@gmail.com", "정상회원", null);                     // 탈퇴하지 않음
        Member old2 = member("old2@gmail.com", "오래전2", CUTOFF);        // 기준 시각 정확히 = 포함
        Member suspended = member("susp@gmail.com", "정지회원", null);
        ReflectionTestUtilsHelper.setStatus(suspended, Member.STATUS_SUSPENDED);
        memberRepository.saveAndFlush(suspended);

        List<Member> targets = memberRepository.findPurgeTargets(CUTOFF, PageRequest.of(0, 100));

        assertThat(targets).extracting(Member::getMemberId).containsExactly(old1.getMemberId(), old2.getMemberId());
    }

    @Test
    @DisplayName("이미 지운 회원은 다시 나오지 않는다")
    void purgedMembersAreNotReturnedAgain() {
        Member member = member("old@gmail.com", "오래전", CUTOFF.minusDays(5));

        member.purgePersonalInfo();
        memberRepository.saveAndFlush(member);

        assertThat(memberRepository.findPurgeTargets(CUTOFF, PageRequest.of(0, 100))).isEmpty();
    }

    @Test
    @DisplayName("한 번에 가져오는 수를 제한한다")
    void respectsBatchSize() {
        for (int i = 1; i <= 5; i++) {
            member("old" + i + "@gmail.com", "오래전" + i, CUTOFF.minusDays(i));
        }

        assertThat(memberRepository.findPurgeTargets(CUTOFF, PageRequest.of(0, 3))).hasSize(3);
    }

    @Test
    @DisplayName("지운 회원의 닉네임('탈퇴한 사용자#ID')은 서로 겹치지 않는다")
    void purgedNicknamesAreUnique() {
        Member a = member("a@gmail.com", "가", CUTOFF.minusDays(2));
        Member b = member("b@gmail.com", "나", CUTOFF.minusDays(3));

        a.purgePersonalInfo();
        b.purgePersonalInfo();
        memberRepository.flush();

        assertThat(a.getNickname()).isNotEqualTo(b.getNickname());
    }

    @Test
    @DisplayName("사용했거나 만료된 토큰은 지우고, 아직 쓸 수 있는(쓰지 않았고 만료 전인) 토큰은 남긴다 (DB설계 3-2)")
    void deletesUsedOrExpiredTokensOnly() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 9, 3, 0);
        tokenRepository.save(PasswordResetToken.issue(1L, "expired-hash", now.minusMinutes(30)));   // 20분 전에 만료, 쓰지 않음
        PasswordResetToken used = PasswordResetToken.issue(2L, "used-hash", now.minusMinutes(2));    // 8분 뒤 만료지만 이미 사용
        used.markUsed(now.minusMinutes(1));
        tokenRepository.save(used);
        tokenRepository.save(PasswordResetToken.issue(3L, "fresh-hash", now.minusMinutes(1)));      // 9분 뒤 만료, 쓰지 않음
        tokenRepository.flush();

        int deleted = tokenRepository.deleteUsedOrExpired(now);

        assertThat(deleted).isEqualTo(2);
        assertThat(tokenRepository.findByTokenHash("expired-hash")).isEmpty();
        assertThat(tokenRepository.findByTokenHash("used-hash")).isEmpty();
        assertThat(tokenRepository.findByTokenHash("fresh-hash")).isPresent();
    }

    /** Member 의 상태 필드를 직접 바꾸는 테스트 도우미 (정지 상태를 만들 공개 메서드가 아직 없다) */
    private static final class ReflectionTestUtilsHelper {
        static void setStatus(Member member, String status) {
            org.springframework.test.util.ReflectionTestUtils.setField(member, "status", status);
        }
    }
}
