package com.wanted.moneybeetle.domain.quiz.model.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanted.moneybeetle.MoneyBeetleApplication;
import com.wanted.moneybeetle.domain.quiz.model.dao.QuizChoiceRepository;
import com.wanted.moneybeetle.domain.quiz.model.dao.QuizQuestionRepository;
import com.wanted.moneybeetle.domain.quiz.model.dto.CourseQuizStatDTO;
import com.wanted.moneybeetle.domain.quiz.model.dto.QuizSaveDTO;
import com.wanted.moneybeetle.domain.quiz.model.entity.QuizChoice;
import com.wanted.moneybeetle.domain.quiz.model.entity.QuizQuestion;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
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
*   담당 : DBA ① (강의·퀴즈)
*   #384 : 푼 기록이 없는 문항에서 보기·정답만 바꿔도 문항 수정 시각(updated_at)이 갱신되는지 실제 쿼리(H2)로 확인한다
*   - 처음 저장 시각(감사 기능)은 11-01 09:00, 서비스의 공통 Clock 은 11-20 10:00 으로 고정해 둘을 구별한다
*   - 보기만 바꿈 / 정답만 바꿈 → 바뀐 문항만 11-20 10:00, 나머지 문항은 그대로
*   - 아무것도 안 바꿈 → 그대로 (감사 시각을 앞으로 보내도 UPDATE 가 나가지 않는다)
*   - 보기 개수가 달라 지우고 새로 만듦 → 11-20 10:00
*   - 강사 센터 퀴즈 목록(API 150)의 가장 늦은 수정 시각에도 반영된다
*  */
@EntityScan(basePackages = "com.wanted.moneybeetle.domain.quiz.model.entity")
@EnableJpaRepositories(basePackages = "com.wanted.moneybeetle.domain.quiz.model.dao")
@ContextConfiguration(classes = MoneyBeetleApplication.class)
@Import(InstructorQuizUpdatedAtTest.AuditingConfig.class)
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:instructor-quiz-updated-at-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InstructorQuizUpdatedAtTest {

    private static final long COURSE_ID = 1L;
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 11, 1, 9, 0);
    private static final LocalDateTime SAVED = LocalDateTime.of(2026, 11, 20, 10, 0);

    /** 감사 기능(@LastModifiedDate)이 쓰는 시각. 처음 만들 때 시각으로 고정한다 */
    static final AtomicReference<LocalDateTime> AUDIT_NOW = new AtomicReference<>(CREATED);

    @TestConfiguration
    @EnableJpaAuditing(dateTimeProviderRef = "quizTestDateTimeProvider")
    static class AuditingConfig {
        @Bean
        DateTimeProvider quizTestDateTimeProvider() {
            return () -> Optional.of(AUDIT_NOW.get());
        }
    }

    @Autowired
    private QuizQuestionRepository quizQuestionRepository;
    @Autowired
    private QuizChoiceRepository quizChoiceRepository;
    @Autowired
    private EntityManager em;

    private InstructorQuizService service;

    @BeforeEach
    void setUp() {
        AUDIT_NOW.set(CREATED);
        Clock clock = Clock.fixed(SAVED.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
        service = new InstructorQuizService(quizQuestionRepository, quizChoiceRepository, clock);
    }

    /* 4지선다 5문제 (문항 i 의 보기는 "보기i-1"~"보기i-4", 정답은 첫 번째) */
    private List<QuizSaveDTO.Question> baseQuestions() {
        List<QuizSaveDTO.Question> questions = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            questions.add(new QuizSaveDTO.Question("문제" + i, null,
                    List.of("보기" + i + "-1", "보기" + i + "-2", "보기" + i + "-3", "보기" + i + "-4"), 0, "해설" + i));
        }
        return questions;
    }

    private void save(List<QuizSaveDTO.Question> questions) {
        service.saveMainQuiz(COURSE_ID, new QuizSaveDTO(questions));
        em.flush();
        em.clear();
    }

    /* 처음 저장 (감사 시각 11-01 09:00) */
    private void saveInitial() {
        save(baseQuestions());
        assertThat(updatedAts()).containsOnly(CREATED);
    }

    private List<LocalDateTime> updatedAts() {
        return quizQuestionRepository.findByCourseIdAndQuizTypeOrderBySortOrderAsc(COURSE_ID, QuizQuestion.QUIZ_TYPE_MAIN).stream()
                .map(QuizQuestion::getUpdatedAt).toList();
    }

    @Test
    @DisplayName("보기 내용만 바꾸면 그 문항의 수정 시각만 저장 시각으로 바뀐다")
    void choiceTextChangeTouchesQuestion() {
        saveInitial();
        List<QuizSaveDTO.Question> next = new ArrayList<>(baseQuestions());
        next.set(1, new QuizSaveDTO.Question("문제2", null, List.of("보기2-1", "고친 보기", "보기2-3", "보기2-4"), 0, "해설2"));

        save(next);

        assertThat(updatedAts()).containsExactly(CREATED, SAVED, CREATED, CREATED, CREATED);
    }

    @Test
    @DisplayName("정답만 바꿔도 그 문항의 수정 시각이 바뀌고, 문항 ID 는 그대로다")
    void answerChangeTouchesQuestion() {
        saveInitial();
        Long thirdId = quizQuestionRepository.findByCourseIdAndQuizTypeOrderBySortOrderAsc(COURSE_ID, QuizQuestion.QUIZ_TYPE_MAIN)
                .get(2).getQuizQuestionId();
        List<QuizSaveDTO.Question> next = new ArrayList<>(baseQuestions());
        next.set(2, new QuizSaveDTO.Question("문제3", null, List.of("보기3-1", "보기3-2", "보기3-3", "보기3-4"), 3, "해설3"));

        save(next);

        List<QuizQuestion> after = quizQuestionRepository.findByCourseIdAndQuizTypeOrderBySortOrderAsc(COURSE_ID, QuizQuestion.QUIZ_TYPE_MAIN);
        assertThat(after.get(2).getQuizQuestionId()).isEqualTo(thirdId);
        assertThat(after).extracting(QuizQuestion::getUpdatedAt).containsExactly(CREATED, CREATED, SAVED, CREATED, CREATED);
    }

    @Test
    @DisplayName("아무것도 안 바꾸고 다시 저장하면 수정 시각은 그대로다")
    void noChangeKeepsUpdatedAt() {
        saveInitial();
        AUDIT_NOW.set(SAVED);   // 감사 시각도 앞으로 보낸다 : 불필요한 UPDATE 가 나가면 updated_at 이 SAVED 로 바뀌어 잡힌다

        save(baseQuestions());

        assertThat(updatedAts()).containsOnly(CREATED);
    }

    @Test
    @DisplayName("기존 보기 개수가 달라 지우고 새로 만들면 수정 시각이 바뀐다")
    void recreatedChoicesTouchQuestion() {
        QuizQuestion old = quizQuestionRepository.save(QuizQuestion.create(COURSE_ID, QuizQuestion.QUIZ_TYPE_MAIN,
                QuizQuestion.QUESTION_TYPE_CHOICE, "문제1", "해설1", 1));
        quizChoiceRepository.saveAll(List.of(   // 예전 데이터처럼 보기가 3개뿐인 문항
                QuizChoice.create(old.getQuizQuestionId(), "보기1-1", true, 1),
                QuizChoice.create(old.getQuizQuestionId(), "보기1-2", false, 2),
                QuizChoice.create(old.getQuizQuestionId(), "보기1-3", false, 3)));
        em.flush();
        em.clear();

        save(baseQuestions());

        List<QuizQuestion> after = quizQuestionRepository.findByCourseIdAndQuizTypeOrderBySortOrderAsc(COURSE_ID, QuizQuestion.QUIZ_TYPE_MAIN);
        assertThat(after.get(0).getQuizQuestionId()).isEqualTo(old.getQuizQuestionId());
        assertThat(after.get(0).getUpdatedAt()).isEqualTo(SAVED);
        assertThat(quizChoiceRepository.findByQuizQuestionIdInOrderBySortOrderAsc(List.of(old.getQuizQuestionId()))).hasSize(4);
    }

    @Test
    @DisplayName("강사 센터 퀴즈 목록(API 150)의 가장 늦은 수정 시각도 보기만 고친 저장 시각이 된다")
    void quizStatsReflectChoiceChange() {
        saveInitial();
        List<QuizSaveDTO.Question> next = new ArrayList<>(baseQuestions());
        next.set(4, new QuizSaveDTO.Question("문제5", null, List.of("보기5-1", "보기5-2", "보기5-3", "새 보기"), 0, "해설5"));

        save(next);

        assertThat(quizQuestionRepository.findQuizStatsByCourseIds(List.of(COURSE_ID)))
                .containsExactly(new CourseQuizStatDTO(COURSE_ID, 0, 5, SAVED));
    }
}
