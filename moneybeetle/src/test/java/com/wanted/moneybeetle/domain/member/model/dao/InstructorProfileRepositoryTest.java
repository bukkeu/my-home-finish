package com.wanted.moneybeetle.domain.member.model.dao;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanted.moneybeetle.MoneyBeetleApplication;
import com.wanted.moneybeetle.domain.member.model.entity.InstructorProfile;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import jakarta.persistence.EntityManager;
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
*   담당 : DBA ② (회원·인증)
*   instructor_profile 의 새 컬럼 headline·bio (이슈 #365)가 Entity 와 매핑되어 저장·조회되는지 H2 에서 확인한다.
*   (표는 ddl-auto: create-drop 으로 Entity 에서 만든다. 운영 MySQL 표는 schema.sql·migration 이 같은 컬럼을 가진다)
*  */
@EntityScan(basePackages = "com.wanted.moneybeetle.domain.member.model.entity")
@EnableJpaRepositories(basePackages = "com.wanted.moneybeetle.domain.member.model.dao")
@ContextConfiguration(classes = MoneyBeetleApplication.class)
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:instructor-profile-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InstructorProfileRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private InstructorProfileRepository instructorProfileRepository;
    @Autowired
    private EntityManager entityManager;

    private Member saveMember(String email, String nickname) {
        return memberRepository.saveAndFlush(Member.signup(email, "hash", nickname, LocalDate.of(1990, 1, 2), "FEMALE",
                LocalDate.of(2026, 11, 8)));
    }

    @Test
    @DisplayName("한 줄 소개·강사 소개·쉼표로 이은 전문 분야가 저장되고 다시 읽힌다")
    void savesHeadlineBioAndFields() {
        Member teacher = saveMember("teacher@moneybeetle.kr", "박서연");
        InstructorProfile profile = instructorProfileRepository.saveAndFlush(
                InstructorProfile.create(teacher.getMemberId(), "박서연", "청약"));
        entityManager.clear();

        InstructorProfile found = instructorProfileRepository.findByMemberId(teacher.getMemberId()).orElseThrow();
        assertThat(found.getHeadline()).isNull();
        assertThat(found.getBio()).isNull();

        found.changeProfile("박서연 강사", "청약 가점, 한 번에", "가".repeat(1000), "청약,재테크 기초");
        instructorProfileRepository.flush();
        entityManager.clear();

        InstructorProfile reloaded = instructorProfileRepository.findById(profile.getInstructorProfileId()).orElseThrow();
        assertThat(reloaded.getDisplayName()).isEqualTo("박서연 강사");
        assertThat(reloaded.getHeadline()).isEqualTo("청약 가점, 한 번에");
        assertThat(reloaded.getBio()).hasSize(1000);
        assertThat(reloaded.getField()).isEqualTo("청약,재테크 기초");
        assertThat(reloaded.getApprovalStatus()).isEqualTo(InstructorProfile.PENDING);
    }
}
