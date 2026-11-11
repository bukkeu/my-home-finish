package com.wanted.moneybeetle.domain.quiz.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.course.model.service.CourseService;
import com.wanted.moneybeetle.domain.growth.model.dto.RewardResultDTO;
import com.wanted.moneybeetle.domain.growth.model.entity.RewardRule;
import com.wanted.moneybeetle.domain.growth.model.service.GrowthService;
import com.wanted.moneybeetle.domain.growth.model.service.RewardService;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.operation.model.service.SystemSettingService;
import com.wanted.moneybeetle.domain.quiz.exception.QuizErrorCode;
import com.wanted.moneybeetle.domain.quiz.model.dao.QuizAnswerRepository;
import com.wanted.moneybeetle.domain.quiz.model.dao.QuizAttemptRepository;
import com.wanted.moneybeetle.domain.quiz.model.dao.QuizChoiceRepository;
import com.wanted.moneybeetle.domain.quiz.model.dao.QuizQuestionRepository;
import com.wanted.moneybeetle.domain.quiz.model.dao.WrongAnswerNoteRepository;
import com.wanted.moneybeetle.domain.quiz.model.dto.QuizResultDTO;
import com.wanted.moneybeetle.domain.quiz.model.dto.QuizSubmitDTO;
import com.wanted.moneybeetle.domain.quiz.model.dto.TestQuizResultDTO;
import com.wanted.moneybeetle.domain.quiz.model.entity.QuizAnswer;
import com.wanted.moneybeetle.domain.quiz.model.entity.QuizAttempt;
import com.wanted.moneybeetle.domain.quiz.model.entity.QuizChoice;
import com.wanted.moneybeetle.domain.quiz.model.entity.QuizQuestion;
import com.wanted.moneybeetle.domain.quiz.model.entity.WrongAnswerNote;
import com.wanted.moneybeetle.global.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : DBA ① 신아림 (#286, QuizService 현재 동작을 고정하는 테스트)
*   다음 이슈에서 채점 코드를 옮기기 전에, 지금 동작을 그대로 확인해 둔다 (main 코드는 바꾸지 않음).
*   - 테스트 퀴즈 채점(gradeTestQuiz) : 정답·오답·미응답·다른 문항의 보기·같은 문항 두 번
*   - 본 퀴즈 제출(submitQuiz)      : 통과 점수 경계(69·70·71점, 설정 70), 오답노트 저장, 처음 통과할 때만 보상
*   - 오답 다시 풀기는 #294에서 WrongNoteService 로 옮겨 WrongNoteServiceTest 에 있다 (검증 내용은 그대로)
*   저장소와 다른 도메인 서비스는 Mockito 가짜로 둔다 (DB 없이)
*  */
class QuizServiceTest {

    private static final Long MEMBER = 8L;
    private static final Long COURSE = 15L;

    private CourseService courseService;
    private QuizQuestionRepository questionRepository;
    private QuizChoiceRepository choiceRepository;
    private WrongAnswerNoteRepository wrongNoteRepository;
    private QuizAttemptRepository attemptRepository;
    private QuizAnswerRepository answerRepository;
    private RewardService rewardService;
    private SystemSettingService settingService;
    private MemberService memberService;
    private QuizService quizService;

    @BeforeEach
    void setUp() {
        courseService = mock(CourseService.class);
        questionRepository = mock(QuizQuestionRepository.class);
        choiceRepository = mock(QuizChoiceRepository.class);
        wrongNoteRepository = mock(WrongAnswerNoteRepository.class);
        attemptRepository = mock(QuizAttemptRepository.class);
        answerRepository = mock(QuizAnswerRepository.class);
        rewardService = mock(RewardService.class);
        settingService = mock(SystemSettingService.class);
        memberService = mock(MemberService.class);
        quizService = new QuizService(courseService, questionRepository, choiceRepository, wrongNoteRepository,
                attemptRepository, answerRepository, rewardService, mock(GrowthService.class), settingService,
                memberService, Clock.fixed(Instant.parse("2026-11-12T01:00:00Z"), ZoneId.of("Asia/Seoul")));

        when(settingService.findIntValue("QUIZ_PASS_SCORE")).thenReturn(OptionalInt.of(70));
        when(courseService.hasWatchedAllLessons(MEMBER, COURSE)).thenReturn(true);
        when(attemptRepository.save(any(QuizAttempt.class))).thenAnswer(invocation -> {
            QuizAttempt attempt = invocation.getArgument(0);
            ReflectionTestUtils.setField(attempt, "quizAttemptId", 100L);
            return attempt;
        });
        when(rewardService.grant(anyLong(), anyString(), anyLong()))
                .thenReturn(RewardResultDTO.granted(50, 100, 0, 1_100, 150, 3, 3, List.of()));
    }

    /* ---------- 테스트 데이터 ---------- */

    private static QuizQuestion question(long id, Long courseId, String quizType, int sortOrder) {
        QuizQuestion question = QuizQuestion.create(courseId, quizType,
                QuizQuestion.QUIZ_TYPE_TEST.equals(quizType) ? QuizQuestion.QUESTION_TYPE_OX : QuizQuestion.QUESTION_TYPE_CHOICE,
                "문제 " + id, "해설 " + id, sortOrder);
        ReflectionTestUtils.setField(question, "quizQuestionId", id);
        return question;
    }

    private static QuizChoice choice(long id, long questionId, boolean correct, int sortOrder) {
        QuizChoice choice = QuizChoice.create(questionId, "보기 " + id, correct, sortOrder);
        ReflectionTestUtils.setField(choice, "quizChoiceId", id);
        return choice;
    }

    /* 문항 n개(ID 1..n). 문항 k의 보기는 k*10+1(정답), k*10+2(오답) */
    private List<QuizQuestion> givenQuestions(String quizType, int count) {
        List<QuizQuestion> questions = new ArrayList<>();
        List<QuizChoice> choices = new ArrayList<>();
        for (long k = 1; k <= count; k++) {
            questions.add(question(k, COURSE, quizType, (int) k));
            choices.add(choice(k * 10 + 1, k, true, 1));
            choices.add(choice(k * 10 + 2, k, false, 2));
        }
        when(questionRepository.findByCourseIdAndQuizTypeOrderBySortOrderAsc(COURSE, quizType)).thenReturn(questions);
        when(choiceRepository.findByQuizQuestionIdInOrderBySortOrderAsc(anyList())).thenReturn(choices);
        return questions;
    }

    private static long correct(long questionId) { return questionId * 10 + 1; }

    private static long wrong(long questionId) { return questionId * 10 + 2; }

    /* 답안 목록. 짝으로 (문항 ID, 보기 ID) 를 넘긴다. 보기 ID 가 null 이면 미응답 */
    private static QuizSubmitDTO answers(Long... pairs) {
        List<QuizSubmitDTO.AnswerItem> items = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            QuizSubmitDTO.AnswerItem item = new QuizSubmitDTO.AnswerItem();
            ReflectionTestUtils.setField(item, "questionId", pairs[i]);
            ReflectionTestUtils.setField(item, "choiceId", pairs[i + 1]);
            items.add(item);
        }
        QuizSubmitDTO request = new QuizSubmitDTO();
        ReflectionTestUtils.setField(request, "answers", items);
        return request;
    }

    /* 문항 n개 중 앞의 k개만 맞힌 답안 */
    private static QuizSubmitDTO answersWithCorrect(int count, int correctCount) {
        Long[] pairs = new Long[count * 2];
        for (int k = 1; k <= count; k++) {
            pairs[(k - 1) * 2] = (long) k;
            pairs[(k - 1) * 2 + 1] = k <= correctCount ? correct(k) : wrong(k);
        }
        return answers(pairs);
    }

    private static TestQuizResultDTO.ResultItem resultOf(List<TestQuizResultDTO.ResultItem> results, long questionId) {
        return results.stream().filter(r -> r.getQuestionId().equals(questionId)).findFirst().orElseThrow();
    }

    /* ===================== 테스트 퀴즈 채점 (API 40) ===================== */

    @Nested
    @DisplayName("테스트 퀴즈 채점 (gradeTestQuiz)")
    class GradeTestQuiz {

        @BeforeEach
        void questions() {
            givenQuestions(QuizQuestion.QUIZ_TYPE_TEST, 2);
        }

        @Test
        @DisplayName("정답 보기를 고르면 맞음, 정답 보기 ID와 해설을 돌려준다")
        void correctChoice() {
            TestQuizResultDTO result = quizService.gradeTestQuiz(COURSE, answers(1L, correct(1), 2L, correct(2)));

            assertThat(resultOf(result.getResults(), 1).isCorrect()).isTrue();
            assertThat(resultOf(result.getResults(), 1).getCorrectChoiceId()).isEqualTo(correct(1));
            assertThat(resultOf(result.getResults(), 1).getExplanation()).isEqualTo("해설 1");
            assertThat(result.getCorrectCount()).isEqualTo(2);
            assertThat(result.isLessonsUnlocked()).isTrue();
        }

        @Test
        @DisplayName("오답 보기를 고르면 틀림. 그래도 모든 문항에 그 문항의 보기를 골랐으면 영상이 열린다")
        void wrongChoice() {
            TestQuizResultDTO result = quizService.gradeTestQuiz(COURSE, answers(1L, wrong(1), 2L, correct(2)));

            assertThat(resultOf(result.getResults(), 1).isCorrect()).isFalse();
            assertThat(result.getCorrectCount()).isEqualTo(1);
            assertThat(result.isLessonsUnlocked()).isTrue();
        }

        @Test
        @DisplayName("답을 안 고르면(choiceId null) 틀림이고 영상은 열리지 않는다")
        void noChoice() {
            TestQuizResultDTO result = quizService.gradeTestQuiz(COURSE, answers(1L, null, 2L, correct(2)));

            assertThat(resultOf(result.getResults(), 1).isCorrect()).isFalse();
            assertThat(result.isLessonsUnlocked()).isFalse();
        }

        @Test
        @DisplayName("다른 문항의 보기 ID를 보내면 무효: 그 문항의 정답이어도 틀림이고 영상은 열리지 않는다")
        void choiceOfOtherQuestion() {
            // 문항 1에 문항 2의 정답 보기(21)를 보낸다
            TestQuizResultDTO result = quizService.gradeTestQuiz(COURSE, answers(1L, correct(2), 2L, correct(2)));

            assertThat(resultOf(result.getResults(), 1).isCorrect()).isFalse();
            assertThat(result.isLessonsUnlocked()).isFalse();
        }

        @Test
        @DisplayName("같은 문항을 두 번 보내면 첫 답만 쓴다 (첫 답이 미응답이어도 그대로)")
        void firstAnswerWins() {
            TestQuizResultDTO wrongFirst = quizService.gradeTestQuiz(COURSE,
                    answers(1L, wrong(1), 1L, correct(1), 2L, correct(2)));
            TestQuizResultDTO correctFirst = quizService.gradeTestQuiz(COURSE,
                    answers(1L, correct(1), 1L, wrong(1), 2L, correct(2)));
            TestQuizResultDTO nullFirst = quizService.gradeTestQuiz(COURSE,
                    answers(1L, null, 1L, correct(1), 2L, correct(2)));

            assertThat(resultOf(wrongFirst.getResults(), 1).isCorrect()).isFalse();
            assertThat(resultOf(correctFirst.getResults(), 1).isCorrect()).isTrue();
            assertThat(resultOf(nullFirst.getResults(), 1).isCorrect()).isFalse();
        }

        @Test
        @DisplayName("이 강의에 없는 문항 ID는 채점하지 않고, 결과는 강의 문항 순서대로 나온다")
        void onlyCourseQuestions() {
            TestQuizResultDTO result = quizService.gradeTestQuiz(COURSE, answers(99L, 991L, 2L, correct(2), 1L, correct(1)));

            assertThat(result.getResults()).extracting(TestQuizResultDTO.ResultItem::getQuestionId).containsExactly(1L, 2L);
        }

        @Test
        @DisplayName("채점만 하고 아무것도 저장하지 않으며, 강의가 보이는지 먼저 확인한다")
        void savesNothing() {
            quizService.gradeTestQuiz(COURSE, answers(1L, correct(1), 2L, wrong(2)));

            verify(courseService).checkCourseVisible(COURSE);
            verifyNoInteractions(attemptRepository, answerRepository, wrongNoteRepository, rewardService);
        }
    }

    /* ===================== 본 퀴즈 제출 (API 42) ===================== */

    @Nested
    @DisplayName("본 퀴즈 제출 (submitQuiz)")
    class SubmitQuiz {

        @Test
        @DisplayName("통과 점수 70 기준: 69점(9/13)은 불합격이고 학습 완료·보상이 없다")
        void score69Fails() {
            givenQuestions(QuizQuestion.QUIZ_TYPE_MAIN, 13);

            QuizResultDTO result = quizService.submitQuiz(MEMBER, COURSE, answersWithCorrect(13, 9));

            assertThat(result.score()).isEqualTo(69);
            assertThat(result.isPassed()).isFalse();
            assertThat(result.rewards().firstPass()).isFalse();
            verify(courseService, never()).completeLearning(anyLong(), anyLong());
            verify(rewardService, never()).grant(anyLong(), anyString(), anyLong());
        }

        @Test
        @DisplayName("통과 점수 70 기준: 70점(7/10)은 합격이고 처음 통과면 학습 완료 보상을 준다")
        void score70Passes() {
            givenQuestions(QuizQuestion.QUIZ_TYPE_MAIN, 10);
            when(courseService.completeLearning(MEMBER, COURSE)).thenReturn(true);

            QuizResultDTO result = quizService.submitQuiz(MEMBER, COURSE, answersWithCorrect(10, 7));

            assertThat(result.score()).isEqualTo(70);
            assertThat(result.isPassed()).isTrue();
            assertThat(result.rewards().firstPass()).isTrue();
            verify(rewardService).grant(MEMBER, RewardRule.COURSE_COMPLETE, COURSE);
            verify(rewardService, never()).grant(MEMBER, RewardRule.QUIZ_PERFECT, COURSE);   // 만점이 아니면 만점 보상 없음
        }

        @Test
        @DisplayName("통과 점수 70 기준: 71점(5/7, 소수점 버림)은 합격")
        void score71Passes() {
            givenQuestions(QuizQuestion.QUIZ_TYPE_MAIN, 7);
            when(courseService.completeLearning(MEMBER, COURSE)).thenReturn(true);

            QuizResultDTO result = quizService.submitQuiz(MEMBER, COURSE, answersWithCorrect(7, 5));

            assertThat(result.score()).isEqualTo(71);
            assertThat(result.isPassed()).isTrue();
        }

        @Test
        @DisplayName("통과 점수 설정이 없으면 70점을 쓴다")
        void defaultPassScore() {
            when(settingService.findIntValue("QUIZ_PASS_SCORE")).thenReturn(OptionalInt.empty());
            givenQuestions(QuizQuestion.QUIZ_TYPE_MAIN, 10);

            assertThat(quizService.submitQuiz(MEMBER, COURSE, answersWithCorrect(10, 7)).isPassed()).isTrue();   // 70점

            givenQuestions(QuizQuestion.QUIZ_TYPE_MAIN, 13);
            assertThat(quizService.submitQuiz(MEMBER, COURSE, answersWithCorrect(13, 9)).isPassed()).isFalse();  // 69점
        }

        @Test
        @DisplayName("응시 기록과 문항별 답안을 저장한다. 다른 문항의 보기를 고른 답은 보기 없이(null) 오답으로 남긴다")
        void savesAttemptAndAnswers() {
            givenQuestions(QuizQuestion.QUIZ_TYPE_MAIN, 3);

            quizService.submitQuiz(MEMBER, COURSE, answers(1L, correct(1), 2L, correct(3), 3L, null));

            ArgumentCaptor<QuizAttempt> attempt = ArgumentCaptor.forClass(QuizAttempt.class);
            verify(attemptRepository).save(attempt.capture());
            assertThat(attempt.getValue().getQuestionCount()).isEqualTo(3);
            assertThat(attempt.getValue().getCorrectCount()).isEqualTo(1);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<QuizAnswer>> saved = ArgumentCaptor.forClass(List.class);
            verify(answerRepository).saveAll(saved.capture());
            assertThat(saved.getValue()).extracting(QuizAnswer::getQuizQuestionId).containsExactly(1L, 2L, 3L);
            assertThat(saved.getValue()).extracting(QuizAnswer::getSelectedChoiceId).containsExactly(correct(1), null, null);
            assertThat(saved.getValue()).extracting(QuizAnswer::isCorrect).containsExactly(true, false, false);
            assertThat(saved.getValue()).extracting(QuizAnswer::getQuizAttemptId).containsOnly(100L);
        }

        @Test
        @DisplayName("틀린 문항만 오답노트에 저장한다. 이미 있는 문항은 새로 만들지 않고 마지막 오답만 바꾼다")
        void wrongNotes() {
            givenQuestions(QuizQuestion.QUIZ_TYPE_MAIN, 3);
            WrongAnswerNote existing = WrongAnswerNote.create(MEMBER, 2L, wrong(2));
            when(wrongNoteRepository.findByMemberIdAndQuizQuestionIdIn(eq(MEMBER), anyList())).thenReturn(List.of(existing));

            // 1번 정답, 2번 미응답(이미 오답노트에 있음), 3번 오답
            quizService.submitQuiz(MEMBER, COURSE, answers(1L, correct(1), 2L, null, 3L, wrong(3)));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Long>> asked = ArgumentCaptor.forClass(List.class);
            verify(wrongNoteRepository).findByMemberIdAndQuizQuestionIdIn(eq(MEMBER), asked.capture());
            assertThat(asked.getValue()).containsExactly(2L, 3L);             // 맞힌 1번은 찾지도 않는다

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<WrongAnswerNote>> created = ArgumentCaptor.forClass(List.class);
            verify(wrongNoteRepository).saveAll(created.capture());
            assertThat(created.getValue()).extracting(WrongAnswerNote::getQuizQuestionId).containsExactly(3L);
            assertThat(created.getValue().get(0).getLastChoiceId()).isEqualTo(wrong(3));
            assertThat(created.getValue().get(0).getMemberId()).isEqualTo(MEMBER);
            assertThat(existing.getLastChoiceId()).isNull();                  // 이번에 안 풀었으니 마지막 오답은 null
        }

        @Test
        @DisplayName("다 맞히면 오답노트를 건드리지 않는다")
        void noWrongNotesWhenPerfect() {
            givenQuestions(QuizQuestion.QUIZ_TYPE_MAIN, 5);

            quizService.submitQuiz(MEMBER, COURSE, answersWithCorrect(5, 5));

            verifyNoInteractions(wrongNoteRepository);
        }

        @Test
        @DisplayName("이미 학습 완료한 강의를 다시 통과하면 보상을 주지 않는다 (처음 통과할 때만)")
        void rewardOnlyFirstPass() {
            givenQuestions(QuizQuestion.QUIZ_TYPE_MAIN, 5);
            when(courseService.completeLearning(MEMBER, COURSE)).thenReturn(false);   // 이미 완료

            QuizResultDTO result = quizService.submitQuiz(MEMBER, COURSE, answersWithCorrect(5, 5));

            assertThat(result.isPassed()).isTrue();
            assertThat(result.rewards().firstPass()).isFalse();
            assertThat(result.rewards().exp()).isZero();
            verify(rewardService, never()).grant(anyLong(), anyString(), anyLong());
        }

        @Test
        @DisplayName("처음 통과가 만점이면 학습 완료 보상과 만점 보상을 함께 주고 합쳐서 돌려준다")
        void perfectFirstPass() {
            givenQuestions(QuizQuestion.QUIZ_TYPE_MAIN, 5);
            when(courseService.completeLearning(MEMBER, COURSE)).thenReturn(true);

            QuizResultDTO result = quizService.submitQuiz(MEMBER, COURSE, answersWithCorrect(5, 5));

            verify(rewardService).grant(MEMBER, RewardRule.COURSE_COMPLETE, COURSE);
            verify(rewardService).grant(MEMBER, RewardRule.QUIZ_PERFECT, COURSE);
            assertThat(result.rewards().exp()).isEqualTo(100);                 // 50 + 50
            assertThat(result.rewards().cash()).isEqualTo(200);                // 100 + 100
            assertThat(result.rewards().levelUp()).isNull();                   // 레벨은 그대로(3 → 3)
        }

        @Test
        @DisplayName("회원 행을 먼저 잠그고, 영상을 다 보지 않았으면 QUIZ_LOCKED 로 아무것도 저장하지 않는다")
        void lockedBeforeWatchingAll() {
            givenQuestions(QuizQuestion.QUIZ_TYPE_MAIN, 5);
            when(courseService.hasWatchedAllLessons(MEMBER, COURSE)).thenReturn(false);

            assertThatThrownBy(() -> quizService.submitQuiz(MEMBER, COURSE, answersWithCorrect(5, 5)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(QuizErrorCode.QUIZ_LOCKED);
            verify(memberService).lockBalance(MEMBER);
            verifyNoInteractions(attemptRepository, answerRepository, wrongNoteRepository, rewardService);
        }
    }
}
