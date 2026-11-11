package com.wanted.moneybeetle.domain.course.model.dao;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanted.moneybeetle.MoneyBeetleApplication;
import com.wanted.moneybeetle.domain.course.model.entity.Course;
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
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : DBA ① 신아림 (#296)
*   통합 검색 쿼리가 Limit 만큼만 가져오고(최근 강의부터), 보이는 강의(허가+공개+미삭제)만 찾는지 실제 쿼리(H2)로 확인한다
*  */
@EntityScan(basePackages = "com.wanted.moneybeetle.domain.course.model.entity")
@EnableJpaRepositories(basePackages = "com.wanted.moneybeetle.domain.course.model.dao")
@ContextConfiguration(classes = MoneyBeetleApplication.class)
@Import(CourseSearchRepositoryTest.AuditingConfig.class)
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:course-search-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CourseSearchRepositoryTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 11, 12, 0);

    @TestConfiguration
    @EnableJpaAuditing(dateTimeProviderRef = "testDateTimeProvider")
    static class AuditingConfig {
        @Bean
        DateTimeProvider testDateTimeProvider() {
            return () -> Optional.of(NOW);
        }
    }

    @Autowired
    private CourseRepository courseRepository;

    private Course course(String title, boolean isPublic, boolean isDeleted) {
        Course course = Course.create(2L, 21L, title, "소개", null, null);
        ReflectionTestUtils.setField(course, "status", Course.STATUS_APPROVED);
        ReflectionTestUtils.setField(course, "isPublic", isPublic);
        if (isDeleted) {
            ReflectionTestUtils.setField(course, "deletedAt", NOW);
        }
        return courseRepository.saveAndFlush(course);
    }

    private List<Course> search(String keyword, int limit) {
        return courseRepository.findByTitleContainingAndStatusAndIsPublicTrueAndDeletedAtIsNullOrderByCourseIdDesc(
                keyword, Course.STATUS_APPROVED, Limit.of(limit));
    }

    @Test
    @DisplayName("키워드가 든 보이는 강의를 최근 강의부터 Limit 개까지만 가져온다")
    void limitedNewestFirst() {
        Course first = course("청약 기초 1", true, false);
        Course second = course("청약 기초 2", true, false);
        Course third = course("청약 기초 3", true, false);
        course("ETF 입문", true, false);

        assertThat(search("청약", 2)).extracting(Course::getCourseId)
                .containsExactly(third.getCourseId(), second.getCourseId());
        assertThat(search("청약", 50)).extracting(Course::getCourseId)
                .containsExactly(third.getCourseId(), second.getCourseId(), first.getCourseId());
    }

    @Test
    @DisplayName("비공개·삭제 강의는 Limit 안에 자리를 차지하지 않는다")
    void hiddenAndDeletedExcludedBeforeLimit() {
        Course visible = course("청약 가점", true, false);
        course("청약 비공개", false, false);
        course("청약 삭제", true, true);

        assertThat(search("청약", 1)).extracting(Course::getCourseId).containsExactly(visible.getCourseId());
    }
}
