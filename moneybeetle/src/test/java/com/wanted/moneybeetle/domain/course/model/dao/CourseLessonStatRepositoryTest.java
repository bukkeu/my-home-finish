package com.wanted.moneybeetle.domain.course.model.dao;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanted.moneybeetle.MoneyBeetleApplication;
import com.wanted.moneybeetle.domain.course.model.entity.Course;
import com.wanted.moneybeetle.domain.course.model.entity.CourseLevel;
import com.wanted.moneybeetle.domain.course.model.entity.Lesson;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
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
*   담당 : PM 이재성 (#369, course 도메인 담당 신아림이 나눠 준 작업)
*   강사 내 강의 목록(API 72)의 구성 집계 : 강의 여러 개의 영상 수·재생 시간 합을 group by 한 번으로 센다.
*   영상이 없는 강의는 결과에 없다(Service 가 0으로 채운다). 난이도는 DB 에 이름 그대로 저장·조회된다
*  */
@EntityScan(basePackages = "com.wanted.moneybeetle.domain.course.model.entity")
@EnableJpaRepositories(basePackages = "com.wanted.moneybeetle.domain.course.model.dao")
@ContextConfiguration(classes = MoneyBeetleApplication.class)
@Import(CourseLessonStatRepositoryTest.AuditingConfig.class)
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:course-lesson-stat-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CourseLessonStatRepositoryTest {

    @TestConfiguration
    @EnableJpaAuditing(dateTimeProviderRef = "testDateTimeProvider")
    static class AuditingConfig {
        @Bean
        DateTimeProvider testDateTimeProvider() {
            return () -> Optional.of(LocalDateTime.of(2026, 11, 12, 10, 0));
        }
    }

    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private LessonRepository lessonRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("강의별 영상 수와 재생 시간 합을 한 번에 센다. 영상이 없는 강의와 목록 밖 강의는 빠진다")
    void lessonStats() {
        Course three = courseRepository.save(Course.create(2L, 21L, "영상 셋", "소개", CourseLevel.ADVANCED, null, null));
        Course one = courseRepository.save(Course.create(2L, 21L, "영상 하나", "소개", null, null));
        Course none = courseRepository.save(Course.create(2L, 21L, "영상 없음", "소개", null, null));
        Course outside = courseRepository.save(Course.create(2L, 21L, "목록 밖", "소개", null, null));
        lessonRepository.save(Lesson.create(three.getCourseId(), 1, "1강", "/v/1.mp4", 600, null));
        lessonRepository.save(Lesson.create(three.getCourseId(), 2, "2강", "/v/2.mp4", 500, null));
        lessonRepository.save(Lesson.create(three.getCourseId(), 3, "3강", "/v/3.mp4", 420, null));
        lessonRepository.save(Lesson.create(one.getCourseId(), 1, "1강", "/v/4.mp4", 90, null));
        lessonRepository.save(Lesson.create(outside.getCourseId(), 1, "1강", "/v/5.mp4", 999, null));
        entityManager.flush();
        entityManager.clear();

        Map<Long, CourseRepository.CourseLessonStat> stats = courseRepository
                .findLessonStatsByCourseIds(List.of(three.getCourseId(), one.getCourseId(), none.getCourseId()))
                .stream()
                .collect(Collectors.toMap(CourseRepository.CourseLessonStat::getCourseId, s -> s));

        assertThat(stats).containsOnlyKeys(three.getCourseId(), one.getCourseId());
        assertThat(stats.get(three.getCourseId()).getLessons()).isEqualTo(3);
        assertThat(stats.get(three.getCourseId()).getTotalDurationSeconds()).isEqualTo(1520);
        assertThat(stats.get(one.getCourseId()).getLessons()).isEqualTo(1);
        assertThat(stats.get(one.getCourseId()).getTotalDurationSeconds()).isEqualTo(90);

        // 난이도 : 고른 값은 그대로, 안 고른 강의는 입문
        assertThat(courseRepository.findById(three.getCourseId())).get()
                .extracting(Course::getLevel).isEqualTo(CourseLevel.ADVANCED);
        assertThat(courseRepository.findById(one.getCourseId())).get()
                .extracting(Course::getLevel).isEqualTo(CourseLevel.BEGINNER);
    }
}
