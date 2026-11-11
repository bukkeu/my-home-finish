package com.wanted.moneybeetle.domain.member.model.dao;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanted.moneybeetle.MoneyBeetleApplication;
import com.wanted.moneybeetle.domain.member.model.dto.InstructorApplicationDTO;
import com.wanted.moneybeetle.domain.member.model.entity.InstructorProfile;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.domain.member.model.entity.MemberStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

/* comment.
*   담당 : DBA ② (회원·인증)
*   Member.status 를 enum 으로 바꾼 뒤(#288)에도 AdminMemberRepository(관리자 회원 목록, #37)의 JPQL 이 문자열 상태 값으로
*   그대로 동작하는지 H2 에서 확인한다. 그 Repository 는 상태를 String 파라미터("ACTIVE")와 문자열 리터럴('WITHDRAWN')로 비교한다.
*  */
@EntityScan(basePackages = "com.wanted.moneybeetle.domain.member.model.entity")
@EnableJpaRepositories(basePackages = "com.wanted.moneybeetle.domain.member.model.dao")
@ContextConfiguration(classes = MoneyBeetleApplication.class)
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-member-status-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AdminMemberRepositoryStatusTest {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private AdminMemberRepository adminMemberRepository;
    @Autowired
    private TestEntityManager entityManager;

    private Member save(String email, String nickname) {
        return memberRepository.saveAndFlush(Member.signup(email, "hash", nickname, LocalDate.of(1999, 4, 12), "FEMALE",
                LocalDate.of(2026, 9, 1)));
    }

    @Test
    @DisplayName("관리자 회원 목록: 문자열 상태(\"ACTIVE\" 등)로 거르면 enum 상태 칼럼과 비교된다")
    void searchByStringStatus() {
        save("active@gmail.com", "정상");
        Member suspended = save("susp@gmail.com", "정지");
        suspended.changeStatus(MemberStatus.SUSPENDED, "광고");
        memberRepository.saveAndFlush(suspended);
        Member withdrawn = save("gone@gmail.com", "탈퇴");
        withdrawn.withdraw(LocalDateTime.of(2026, 10, 1, 9, 0));
        memberRepository.saveAndFlush(withdrawn);

        Page<Member> all = adminMemberRepository.search(null, (String) null, null, PageRequest.of(0, 10));
        Page<Member> onlySuspended = adminMemberRepository.search(null, "SUSPENDED", null, PageRequest.of(0, 10));
        Page<Member> onlyActive = adminMemberRepository.search(null, "ACTIVE", null, PageRequest.of(0, 10));

        assertThat(all.getTotalElements()).isEqualTo(3);
        assertThat(onlySuspended.getContent()).extracting(Member::getNickname).containsExactly("정지");
        assertThat(onlyActive.getContent()).extracting(Member::getNickname).containsExactly("정상");
    }

    @Test
    @DisplayName("강사 가입 신청 목록: 'WITHDRAWN' 문자열 리터럴 비교가 enum 칼럼에서도 동작해 탈퇴 회원의 신청은 목록·개수에서 빠진다")
    void applicationsQueryExcludesWithdrawnMember() {
        Member active = save("active@gmail.com", "정상");
        Member withdrawn = save("gone@gmail.com", "탈퇴");
        LocalDateTime appliedAt = LocalDateTime.of(2026, 10, 1, 9, 0);
        withdrawn.withdraw(appliedAt.plusDays(1));
        memberRepository.saveAndFlush(withdrawn);

        entityManager.persistAndFlush(InstructorProfile.apply(active.getMemberId(), "정상 강사", "청약", appliedAt));
        entityManager.persistAndFlush(InstructorProfile.apply(withdrawn.getMemberId(), "탈퇴 강사", "청약", appliedAt));
        entityManager.clear();

        // 페이지 크기를 결과 수(1)와 같게 해야 Spring Data 가 count 쿼리도 실행해 countQuery 의 탈퇴 제외 조건까지 검증된다
        Page<InstructorApplicationDTO> page = adminMemberRepository.findApplications(InstructorProfile.PENDING, PageRequest.of(0, 1));

        assertThat(page.getContent()).extracting(InstructorApplicationDTO::memberId).containsExactly(active.getMemberId());
        assertThat(page.getTotalElements()).isEqualTo(1);
    }
}
