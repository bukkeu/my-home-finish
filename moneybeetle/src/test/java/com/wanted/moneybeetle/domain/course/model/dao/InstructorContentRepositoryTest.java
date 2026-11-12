package com.wanted.moneybeetle.domain.course.model.dao;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanted.moneybeetle.MoneyBeetleApplication;
import com.wanted.moneybeetle.domain.course.model.entity.Lesson;
import com.wanted.moneybeetle.domain.quiz.model.dao.QuizQuestionRepository;
import com.wanted.moneybeetle.domain.quiz.model.dto.CourseQuizStatDTO;
import com.wanted.moneybeetle.domain.quiz.model.entity.QuizQuestion;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
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
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

/* comment.
*   담당 : DBA ① (강의·퀴즈) - 강사 센터 화면 작업을 DBA ② 김예린이 나눠 받아 만들었다 (#366)
*   강사 센터 목록 쿼리를 실제 쿼리(H2)로 확인한다
*   - 요약글 목록(API 151): 요청한 강의의 영상 중 요약글이 비어 있지 않은 것만, 최근 수정 순
*   - 퀴즈 목록(API 150): 강의마다 지금 쓰는 TEST·MAIN 문항 수와 가장 늦게 바뀐 시각 (보관한 _OLD 문항은 세지 않음)
*  */
@EntityScan(basePackages = {"com.wanted.moneybeetle.domain.course.model.entity",
        "com.wanted.moneybeetle.domain.quiz.model.entity"})
@EnableJpaRepositories(basePackages = {"com.wanted.moneybeetle.domain.course.model.dao",
        "com.wanted.moneybeetle.domain.quiz.model.dao"})
@ContextConfiguration(classes = MoneyBeetleApplication.class)
@Import(InstructorContentRepositoryTest.AuditingConfig.class)
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:instructor-content-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InstructorContentRepositoryTest {

    /** 저장할 때마다 시각을 바꿔서 "최근 수정 순"을 확인할 수 있게 한다 */
    static final AtomicReference<LocalDateTime> NOW = new AtomicReference<>();

    @TestConfiguration
    @EnableJpaAuditing(dateTimeProviderRef = "testDateTimeProvider")
    static class AuditingConfig {
        @Bean
        DateTimeProvider testDateTimeProvider() {
            return () -> Optional.of(NOW.get());
        }
    }

    @Autowired
    private LessonRepository lessonRepository;
    @Autowired
    private QuizQuestionRepository quizQuestionRepository;

    @BeforeEach
    void setUp() {
        NOW.set(LocalDateTime.of(2026, 11, 1, 9, 0));
    }

    private Lesson lesson(long courseId, int order, String summary, int day) {
        NOW.set(LocalDateTime.of(2026, 11, day, 9, 0));
        return lessonRepository.saveAndFlush(Lesson.create(courseId, order, "영상" + courseId + "-" + order, "/uploads/v.mp4", 100, summary));
    }

    private void question(long courseId, String quizType, int order, int day) {
        NOW.set(LocalDateTime.of(2026, 11, day, 9, 0));
        quizQuestionRepository.saveAndFlush(QuizQuestion.create(courseId, quizType,
                "TEST".equals(quizType) || "TEST_OLD".equals(quizType) ? QuizQuestion.QUESTION_TYPE_OX : QuizQuestion.QUESTION_TYPE_CHOICE,
                "문제" + order, "해설", order));
    }

    @Test
    @DisplayName("요약글 목록: 요청한 강의의 영상 중 요약글이 있는 것만(null·공백 제외), 최근 수정 순으로 찾는다")
    void findsLessonsWithSummary() {
        Lesson older = lesson(1, 1, "오래된 요약", 2);
        Lesson newer = lesson(1, 2, "최근 요약", 5);
        lesson(1, 3, null, 6);
        lesson(2, 1, "   ", 7);
        lesson(3, 1, "남의 강의 요약", 8);                // 요청하지 않은 강의

        List<Lesson> found = lessonRepository.findWithSummaryByCourseIds(List.of(1L, 2L));

        assertThat(found).extracting(Lesson::getLessonId).containsExactly(newer.getLessonId(), older.getLessonId());
    }

    @Test
    @DisplayName("요약글 수정용 조회: 영상 행 없이 소속 강의 번호만 꺼내고, 잠금 읽기로 영상을 읽는다 (없으면 빈 값)")
    void findsCourseIdAndLocksLesson() {
        Lesson saved = lesson(7, 1, "요약", 3);

        assertThat(lessonRepository.findCourseIdById(saved.getLessonId())).contains(7L);
        assertThat(lessonRepository.findByIdForUpdate(saved.getLessonId())).map(Lesson::getSummary).contains("요약");
        assertThat(lessonRepository.findCourseIdById(999_999L)).isEmpty();
        assertThat(lessonRepository.findByIdForUpdate(999_999L)).isEmpty();
    }

    @Test
    @DisplayName("퀴즈 목록: 강의마다 TEST·MAIN 문항 수와 가장 늦게 바뀐 시각, 보관한 _OLD 문항과 요청하지 않은 강의는 빠진다")
    void countsQuestionsPerCourse() {
        question(1, "TEST", 1, 2);
        question(1, "TEST", 2, 3);
        question(1, "MAIN", 1, 9);
        question(1, "MAIN_OLD", 2, 20);                    // 보관한 문항: 세지 않고 시각에도 안 들어간다
        question(2, "MAIN_OLD", 1, 4);                     // 보관한 문항뿐이면 행이 나오지 않는다
        question(3, "TEST", 1, 5);                         // 요청하지 않은 강의

        List<CourseQuizStatDTO> stats = quizQuestionRepository.findQuizStatsByCourseIds(List.of(1L, 2L));

        assertThat(stats).containsExactly(new CourseQuizStatDTO(1L, 2, 1, LocalDateTime.of(2026, 11, 9, 9, 0)));
    }
}
