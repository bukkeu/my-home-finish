package com.wanted.moneybeetle.domain.quiz.model.service;

import com.wanted.moneybeetle.domain.course.model.dto.CourseLabelDTO;
import com.wanted.moneybeetle.domain.course.model.service.CourseService;
import com.wanted.moneybeetle.domain.quiz.model.dao.QuizChoiceRepository;
import com.wanted.moneybeetle.domain.quiz.model.dao.QuizQuestionRepository;
import com.wanted.moneybeetle.domain.quiz.model.dao.WrongAnswerNoteRepository;
import com.wanted.moneybeetle.domain.quiz.model.dto.QuizSubmitDTO;
import com.wanted.moneybeetle.domain.quiz.model.dto.WrongNoteDTO;
import com.wanted.moneybeetle.domain.quiz.model.dto.WrongNoteRetryResultDTO;
import com.wanted.moneybeetle.domain.quiz.model.entity.QuizQuestion;
import com.wanted.moneybeetle.domain.quiz.model.entity.WrongAnswerNote;
import com.wanted.moneybeetle.domain.quiz.model.policy.QuestionSheet;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/* comment.
*   담당 : DBA ① 신아림 (#21 오답노트, #294 QuizService 에서 분리)
*   오답노트 조회·다시 풀기·삭제 (API 44~46). 오답을 넣는 일은 퀴즈 제출(QuizService.submitQuiz)이 한다
*   채점 규칙은 퀴즈와 같은 QuestionSheet 를 쓴다
*  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WrongNoteService {

    private final CourseService courseService;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizChoiceRepository quizChoiceRepository;
    private final WrongAnswerNoteRepository wrongAnswerNoteRepository;

    /* API 44. 오답노트: 보이는 강의의 오답만, 카테고리·강의로 거르기 (QST-06, SP-36, DH-01) */
    public Page<WrongNoteDTO> findWrongNotes(Long memberId, Long categoryId, Long courseId, Pageable pageable) {
        Map<Long, CourseLabelDTO> courses = courseService.findVisibleCourseLabels(categoryId, courseId).stream()
                .collect(Collectors.toMap(CourseLabelDTO::getCourseId, Function.identity()));
        if (courses.isEmpty()) {
            return Page.empty(pageable);
        }

        Page<WrongAnswerNote> notes = wrongAnswerNoteRepository.findByMemberIdAndCourseIds(
                memberId, List.copyOf(courses.keySet()), pageable);

        // 이 페이지의 문항·보기를 한 번에 조회 (항목마다 조회하지 않음)
        Map<Long, QuestionSheet> sheets = findSheets(quizQuestionRepository.findAllById(
                notes.getContent().stream().map(WrongAnswerNote::getQuizQuestionId).toList())).stream()
                .collect(Collectors.toMap(QuestionSheet::questionId, Function.identity()));

        return notes.map(note -> {
            QuestionSheet sheet = sheets.get(note.getQuizQuestionId());
            return WrongNoteDTO.of(note, sheet.question(), courses.get(sheet.question().getCourseId()), sheet.choices(),
                    sheet.contentOf(note.getLastChoiceId()), sheet.contentOf(sheet.correctChoiceId()));
        });
    }

    /* API 45. 오답 다시 풀기: 내 오답노트에 있는 문제만 채점하고, 기록·보상은 없다 (QST-06) */
    public WrongNoteRetryResultDTO retryWrongNotes(Long memberId, QuizSubmitDTO request) {
        Map<Long, Long> selected = request.toSelectedMap();
        if (selected.isEmpty()) {
            return WrongNoteRetryResultDTO.of(List.of());
        }

        Set<Long> visibleCourseIds = courseService.findVisibleCourseLabels(null, null).stream()
                .map(CourseLabelDTO::getCourseId)
                .collect(Collectors.toSet());
        List<Long> myQuestionIds = wrongAnswerNoteRepository
                .findByMemberIdAndQuizQuestionIdIn(memberId, List.copyOf(selected.keySet())).stream()
                .map(WrongAnswerNote::getQuizQuestionId)
                .toList();
        // 내 오답노트에 없거나, 비공개가 된 강의의 문제는 채점하지 않는다
        List<QuizQuestion> questions = quizQuestionRepository.findAllById(myQuestionIds).stream()
                .filter(question -> visibleCourseIds.contains(question.getCourseId()))
                .toList();

        return WrongNoteRetryResultDTO.of(QuestionSheet.gradeAll(findSheets(questions), selected));
    }

    /* API 46. 오답 삭제 (실제 삭제). 내 오답만 지워지고, 없거나 남의 것이면 아무 일도 없다 (SP-36) */
    @Transactional
    public void deleteWrongNote(Long memberId, Long wrongNoteId) {
        wrongAnswerNoteRepository.deleteByWrongAnswerNoteIdAndMemberId(wrongNoteId, memberId);
    }

    /* 문항들의 보기를 한 번에 조회해서 문항마다 채점용 시트로 묶는다. 문항이 없으면 조회하지 않는다 */
    private List<QuestionSheet> findSheets(List<QuizQuestion> questions) {
        if (questions.isEmpty()) {
            return List.of();
        }
        List<Long> questionIds = questions.stream().map(QuizQuestion::getQuizQuestionId).toList();
        return QuestionSheet.of(questions, quizChoiceRepository.findByQuizQuestionIdInOrderBySortOrderAsc(questionIds));
    }
}
