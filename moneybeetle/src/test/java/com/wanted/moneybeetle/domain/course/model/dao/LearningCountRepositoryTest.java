package com.wanted.moneybeetle.domain.course.model.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.wanted.moneybeetle.MoneyBeetleApplication;
import com.wanted.moneybeetle.domain.course.model.entity.Course;
import com.wanted.moneybeetle.domain.course.model.entity.CourseStatus;
import com.wanted.moneybeetle.domain.course.model.entity.LearningProgress;
import com.wanted.moneybeetle.domain.course.model.entity.Lesson;
import com.wanted.moneybeetle.domain.course.model.dto.WatchHistoryDTO;
import com.wanted.moneybeetle.domain.course.model.entity.LessonWatch;
import com.wanted.moneybeetle.domain.course.model.service.CategoryPathService;
import com.wanted.moneybeetle.domain.course.model.service.CourseService;
import com.wanted.moneybeetle.domain.member.model.service.InstructorService;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import java.time.Clock;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : DBA ① 신아림 (#188, 홈·내 정보용 개수 전용 조회·마지막 시청 기록 조회)
*   - 개수 전용 조회가 기존 목록 조회(시청 기록 API 34, 학습 완료 API 35)의 전체 개수와 같은 조건으로 센다
*     (이 회원만, 허가 + 공개 + 삭제 안 된 강의만, 학습 완료 상태만)
*   - 마지막 시청 기록 조회가 "최근 시청 목록에서 이 강의의 첫 항목"과 같고, 다른 강의 기록이 아주 많아도 놓치지 않는다
*     (저장소와 CourseService.findLastWatched 둘 다 확인. 서비스가 다시 최근 N건만 넘겨 찾게 바뀌면 실패한다)
*   - 시청 시각이 같으면 나중에 저장된 기록(lessonWatchId 큰 쪽)이 먼저다
*  */
@EntityScan(basePackages = "com.wanted.moneybeetle.domain.course.model.entity")
@EnableJpaRepositories(basePackages = "com.wanted.moneybeetle.domain.course.model.dao")
@ContextConfiguration(classes = MoneyBeetleApplication.class)
@Import(LearningCountRepositoryTest.AuditingConfig.class)
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:learning-count-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LearningCountRepositoryTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 11, 12, 0);
    static final Long ME = 8L;
    static final Long OTHER = 9L;

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
    @Autowired
    private LessonRepository lessonRepository;
    @Autowired
    private LessonWatchRepository watchRepository;
    @Autowired
    private LearningProgressRepository progressRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ScrapRepository scrapRepository;
    @Autowired
    private CourseReviewRepository courseReviewRepository;

    private CourseService courseService;

    private Course visible;
    private Course other;
    private Course hidden;
    private Course deleted;

    @BeforeEach
    void setUp() {
        // 실제 저장소(H2)로 서비스를 만든다. 다른 도메인 서비스는 이 조회에 쓰이지 않아 가짜로 둔다
        courseService = new CourseService(categoryRepository, courseRepository, lessonRepository, progressRepository,
                watchRepository, scrapRepository, courseReviewRepository,
                Clock.fixed(NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul")),
                mock(MemberService.class), mock(InstructorService.class), mock(CategoryPathService.class));
        visible = course("보이는 강의", true, false);
        other = course("다른 보이는 강의", true, false);
        hidden = course("비공개 강의", false, false);
        deleted = course("삭제된 강의", true, true);
    }

    private Course course(String title, boolean isPublic, boolean isDeleted) {
        Course course = Course.create(2L, 21L, title, "소개", null, null);
        ReflectionTestUtils.setField(course, "status", CourseStatus.APPROVED);
        ReflectionTestUtils.setField(course, "isPublic", isPublic);
        if (isDeleted) {
            ReflectionTestUtils.setField(course, "deletedAt", NOW);
        }
        return courseRepository.saveAndFlush(course);
    }

    private Lesson lesson(Course course, int order) {
        return lessonRepository.saveAndFlush(
                Lesson.create(course.getCourseId(), order, "영상 " + order, "/uploads/videos/sample-1.mp4", 600, null));
    }

    private LessonWatch watch(Long memberId, Lesson lesson, int minutesAgo) {
        return watchRepository.saveAndFlush(LessonWatch.start(memberId, lesson.getLessonId(), NOW.minusMinutes(minutesAgo)));
    }

    private void progress(Long memberId, Course course, boolean completed) {
        LearningProgress progress = LearningProgress.start(memberId, course.getCourseId());
        if (completed) {
            progress.complete(NOW);
        }
        progressRepository.saveAndFlush(progress);
    }

    @Test
    @DisplayName("본 영상 수는 시청 기록 목록의 전체 개수와 같고, 비공개·삭제 강의와 다른 회원 기록은 세지 않는다")
    void countWatchedLessons() {
        watch(ME, lesson(visible, 1), 10);
        watch(ME, lesson(visible, 2), 20);
        watch(ME, lesson(other, 1), 30);
        watch(ME, lesson(hidden, 1), 40);
        watch(ME, lesson(deleted, 1), 50);
        watch(OTHER, lesson(visible, 3), 60);

        long count = watchRepository.countVisibleByMemberId(ME, CourseStatus.APPROVED);

        assertThat(count).isEqualTo(3);
        assertThat(count).isEqualTo(watchRepository.findVisibleByMemberId(ME, CourseStatus.APPROVED, PageRequest.of(0, 1))
                .getTotalElements());
    }

    @Test
    @DisplayName("학습 완료 강의 수는 학습 완료 목록의 전체 개수와 같고, 진행 중·비공개·삭제·다른 회원은 세지 않는다")
    void countCompletedCourses() {
        progress(ME, visible, true);
        progress(ME, other, false);
        progress(ME, hidden, true);
        progress(ME, deleted, true);
        progress(OTHER, other, true);

        long count = progressRepository.countVisibleByMemberIdAndStatus(
                ME, LearningProgress.STATUS_COMPLETED, CourseStatus.APPROVED);

        assertThat(count).isEqualTo(1);
        assertThat(count).isEqualTo(progressRepository.findVisibleByMemberIdAndStatus(
                ME, LearningProgress.STATUS_COMPLETED, CourseStatus.APPROVED, PageRequest.of(0, 1)).getTotalElements());
    }

    @Test
    @DisplayName("마지막 시청 기록은 최근 시청 목록에서 이 강의의 첫 항목과 같고, 다른 강의 기록이 많아도 찾는다")
    void findLatestWatchOfCourse() {
        Lesson first = lesson(visible, 1);
        Lesson second = lesson(visible, 2);
        watch(ME, first, 5_000);
        LessonWatch latest = watch(ME, second, 3_000);
        // 이 강의 기록보다 최근인 다른 강의 기록이 아주 많다 (예전 홈은 최근 1,000건까지만 넘겨 보며 찾았다)
        for (int i = 0; i < 1_100; i++) {
            watch(ME, lesson(other, i + 1), i);
        }
        watch(OTHER, second, 0);   // 다른 회원의 더 최근 기록은 상관없다

        List<LessonWatch> found = watchRepository.findLatestVisibleByMemberIdAndCourseId(
                ME, visible.getCourseId(), CourseStatus.APPROVED, PageRequest.of(0, 1));

        assertThat(found).extracting(LessonWatch::getLessonWatchId).containsExactly(latest.getLessonWatchId());
        LessonWatch firstOfCourseInHistory = watchRepository.findVisibleByMemberId(ME, CourseStatus.APPROVED, PageRequest.of(0, 2_000))
                .getContent().stream()
                .filter(w -> w.getLessonId().equals(first.getLessonId()) || w.getLessonId().equals(second.getLessonId()))
                .findFirst().orElseThrow();
        assertThat(found.get(0).getLessonWatchId()).isEqualTo(firstOfCourseInHistory.getLessonWatchId());

        // 서비스도 같은 기록을 돌려준다 (최근 1,000건 안에 없던 기록)
        assertThat(courseService.findLastWatched(ME, visible.getCourseId()))
                .get()
                .extracting(WatchHistoryDTO::getLessonId)
                .isEqualTo(second.getLessonId());
    }

    @Test
    @DisplayName("시청 시각이 같으면 나중에 저장된 기록이 마지막 시청 기록이다")
    void findLatestWatchTieBreak() {
        watch(ME, lesson(visible, 1), 10);
        LessonWatch later = watch(ME, lesson(visible, 2), 10);   // 같은 시각, 나중에 저장

        List<LessonWatch> found = watchRepository.findLatestVisibleByMemberIdAndCourseId(
                ME, visible.getCourseId(), CourseStatus.APPROVED, PageRequest.of(0, 1));

        assertThat(found).extracting(LessonWatch::getLessonWatchId).containsExactly(later.getLessonWatchId());
    }

    @Test
    @DisplayName("비공개 강의이거나 본 적 없으면 마지막 시청 기록이 없다")
    void noLatestWatch() {
        watch(ME, lesson(hidden, 1), 10);

        assertThat(watchRepository.findLatestVisibleByMemberIdAndCourseId(
                ME, hidden.getCourseId(), CourseStatus.APPROVED, PageRequest.of(0, 1))).isEmpty();
        assertThat(watchRepository.findLatestVisibleByMemberIdAndCourseId(
                ME, visible.getCourseId(), CourseStatus.APPROVED, PageRequest.of(0, 1))).isEmpty();
        assertThat(courseService.findLastWatched(ME, hidden.getCourseId())).isEmpty();
    }
}
