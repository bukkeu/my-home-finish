package com.wanted.moneybeetle.domain.quiz.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.course.model.dto.CourseLabelDTO;
import com.wanted.moneybeetle.domain.course.model.service.CourseService;
import com.wanted.moneybeetle.domain.quiz.model.dao.QuizChoiceRepository;
import com.wanted.moneybeetle.domain.quiz.model.dao.QuizQuestionRepository;
import com.wanted.moneybeetle.domain.quiz.model.dao.WrongAnswerNoteRepository;
import com.wanted.moneybeetle.domain.quiz.model.dto.QuizSubmitDTO;
import com.wanted.moneybeetle.domain.quiz.model.dto.TestQuizResultDTO;
import com.wanted.moneybeetle.domain.quiz.model.dto.WrongNoteDTO;
import com.wanted.moneybeetle.domain.quiz.model.dto.WrongNoteRetryResultDTO;
import com.wanted.moneybeetle.domain.quiz.model.entity.QuizChoice;
import com.wanted.moneybeetle.domain.quiz.model.entity.QuizQuestion;
import com.wanted.moneybeetle.domain.quiz.model.entity.WrongAnswerNote;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : DBA ① 신아림 (#294, QuizService 에서 오답노트를 분리)
*   오답 다시 풀기 3개는 #286 QuizServiceTest 에 있던 검증을 그대로 옮겼다 (부르는 서비스만 WrongNoteService 로 바뀜).
*   오답노트 목록(API 44)·삭제(API 46)도 함께 확인한다. 저장소와 다른 도메인 서비스는 Mockito 가짜로 둔다
*  */
class WrongNoteServiceTest {

    private static final Long MEMBER = 8L;
    private static final Long COURSE = 15L;
    private static final Long OTHER_COURSE = 16L;
    private static final Pageable PAGE = PageRequest.of(0, 20);

    private CourseService courseService;
    private QuizQuestionRepository questionRepository;
    private QuizChoiceRepository choiceRepository;
    private WrongAnswerNoteRepository wrongNoteRepository;
    private WrongNoteService wrongNoteService;

    @BeforeEach
    void setUp() {
        courseService = mock(CourseService.class);
        questionRepository = mock(QuizQuestionRepository.class);
        choiceRepository = mock(QuizChoiceRepository.class);
        wrongNoteRepository = mock(WrongAnswerNoteRepository.class);
        wrongNoteService = new WrongNoteService(courseService, questionRepository, choiceRepository, wrongNoteRepository);
    }

    private static QuizQuestion question(long id, Long courseId) {
        QuizQuestion question = QuizQuestion.create(courseId, QuizQuestion.QUIZ_TYPE_MAIN, QuizQuestion.QUESTION_TYPE_CHOICE,
                "문제 " + id, "해설 " + id, 1);
        ReflectionTestUtils.setField(question, "quizQuestionId", id);
        return question;
    }

    private static QuizChoice choice(long id, long questionId, boolean correct, int sortOrder) {
        QuizChoice choice = QuizChoice.create(questionId, "보기 " + id, correct, sortOrder);
        ReflectionTestUtils.setField(choice, "quizChoiceId", id);
        return choice;
    }

    private static long correct(long questionId) { return questionId * 10 + 1; }

    private static long wrong(long questionId) { return questionId * 10 + 2; }

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

    private void givenVisibleCourse() {
        when(courseService.findVisibleCourseLabels(null, null))
                .thenReturn(List.of(CourseLabelDTO.of(COURSE, "청약통장 기초", "부동산 › 청약")));
    }

    /* ---------- 오답 다시 풀기 (API 45) : #286 에서 옮긴 검증 ---------- */

    @Test
    @DisplayName("내 오답노트에 있고 보이는 강의의 문제만 채점한다 (비공개 강의·오답노트에 없는 문제는 빠진다)")
    void gradesOnlyMyVisibleNotes() {
        QuizQuestion mine = question(1, COURSE);
        QuizQuestion hiddenCourse = question(2, OTHER_COURSE);
        givenVisibleCourse();
        when(wrongNoteRepository.findByMemberIdAndQuizQuestionIdIn(eq(MEMBER), anyList()))
                .thenReturn(List.of(WrongAnswerNote.create(MEMBER, 1L, wrong(1)), WrongAnswerNote.create(MEMBER, 2L, wrong(2))));
        when(questionRepository.findAllById(any())).thenReturn(List.of(mine, hiddenCourse));
        when(choiceRepository.findByQuizQuestionIdInOrderBySortOrderAsc(anyList())).thenReturn(List.of(
                choice(correct(1), 1, true, 1), choice(wrong(1), 1, false, 2)));

        // 3번은 내 오답노트에 없는 문제
        WrongNoteRetryResultDTO result = wrongNoteService.retryWrongNotes(MEMBER,
                answers(1L, correct(1), 2L, correct(2), 3L, correct(3)));

        assertThat(result.getResults()).extracting(TestQuizResultDTO.ResultItem::getQuestionId).containsExactly(1L);
        assertThat(result.getResults().get(0).isCorrect()).isTrue();
        assertThat(result.getResults().get(0).getCorrectChoiceId()).isEqualTo(correct(1));
    }

    @Test
    @DisplayName("오답을 고르면 틀림으로 채점하고, 다시 풀기는 아무것도 저장하지 않는다")
    void wrongAndNoRecord() {
        givenVisibleCourse();
        when(wrongNoteRepository.findByMemberIdAndQuizQuestionIdIn(eq(MEMBER), anyList()))
                .thenReturn(List.of(WrongAnswerNote.create(MEMBER, 1L, wrong(1))));
        when(questionRepository.findAllById(any())).thenReturn(List.of(question(1, COURSE)));
        when(choiceRepository.findByQuizQuestionIdInOrderBySortOrderAsc(anyList())).thenReturn(List.of(
                choice(correct(1), 1, true, 1), choice(wrong(1), 1, false, 2)));

        WrongNoteRetryResultDTO result = wrongNoteService.retryWrongNotes(MEMBER, answers(1L, wrong(1)));

        assertThat(result.getResults().get(0).isCorrect()).isFalse();
        verify(wrongNoteRepository, never()).saveAll(anyList());
        verify(wrongNoteRepository, never()).deleteByWrongAnswerNoteIdAndMemberId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("보낸 답이 없으면 빈 결과이고 저장소를 조회하지 않는다")
    void emptyAnswers() {
        WrongNoteRetryResultDTO result = wrongNoteService.retryWrongNotes(MEMBER, answers());

        assertThat(result.getResults()).isEmpty();
        verifyNoInteractions(wrongNoteRepository, questionRepository, courseService, choiceRepository);
    }

    /* ---------- 오답노트 목록 (API 44) ---------- */

    @Test
    @DisplayName("오답노트 항목에 강의 이름표, 내가 고른 보기·정답 보기 내용, 다시 풀기용 보기를 담는다")
    void listWithChoiceContents() {
        when(courseService.findVisibleCourseLabels(null, null))
                .thenReturn(List.of(CourseLabelDTO.of(COURSE, "청약통장 기초", "부동산 › 청약")));
        WrongAnswerNote note = WrongAnswerNote.create(MEMBER, 1L, wrong(1));
        ReflectionTestUtils.setField(note, "wrongAnswerNoteId", 9L);
        WrongAnswerNote unanswered = WrongAnswerNote.create(MEMBER, 2L, null);
        when(wrongNoteRepository.findByMemberIdAndCourseIds(eq(MEMBER), anyList(), eq(PAGE)))
                .thenReturn(new PageImpl<>(List.of(note, unanswered), PAGE, 2));
        when(questionRepository.findAllById(any())).thenReturn(List.of(question(1, COURSE), question(2, COURSE)));
        when(choiceRepository.findByQuizQuestionIdInOrderBySortOrderAsc(anyList())).thenReturn(List.of(
                choice(correct(1), 1, true, 1), choice(wrong(1), 1, false, 2),
                choice(correct(2), 2, true, 1), choice(wrong(2), 2, false, 2)));

        Page<WrongNoteDTO> page = wrongNoteService.findWrongNotes(MEMBER, null, null, PAGE);

        WrongNoteDTO first = page.getContent().get(0);
        assertThat(first.getWrongNoteId()).isEqualTo(9L);
        assertThat(first.getCourseTitle()).isEqualTo("청약통장 기초");
        assertThat(first.getCategoryName()).isEqualTo("부동산 › 청약");
        assertThat(first.getMyChoice()).isEqualTo("보기 " + wrong(1));
        assertThat(first.getCorrectChoice()).isEqualTo("보기 " + correct(1));
        assertThat(first.getChoices()).hasSize(2);
        assertThat(page.getContent().get(1).getMyChoice()).isNull();          // 안 푼 문제는 내 답이 없다
    }

    @Test
    @DisplayName("보이는 강의가 없으면 빈 쪽이고 오답노트를 조회하지 않는다")
    void listEmptyWhenNoVisibleCourse() {
        when(courseService.findVisibleCourseLabels(21L, null)).thenReturn(List.of());

        assertThat(wrongNoteService.findWrongNotes(MEMBER, 21L, null, PAGE).getContent()).isEmpty();
        verifyNoInteractions(wrongNoteRepository);
    }

    /* ---------- 오답 삭제 (API 46) ---------- */

    @Test
    @DisplayName("삭제는 내 오답만 지우도록 회원 ID를 함께 넘긴다")
    void deleteOnlyMine() {
        wrongNoteService.deleteWrongNote(MEMBER, 9L);

        verify(wrongNoteRepository).deleteByWrongAnswerNoteIdAndMemberId(9L, MEMBER);
    }
}
