package com.wanted.moneybeetle.domain.quiz.model.service;

import com.wanted.moneybeetle.domain.course.model.dto.CourseLabelDTO;
import com.wanted.moneybeetle.domain.course.model.service.CourseService;
import com.wanted.moneybeetle.domain.quiz.model.dao.QuizChoiceRepository;
import com.wanted.moneybeetle.domain.quiz.model.dao.QuizQuestionRepository;
import com.wanted.moneybeetle.domain.quiz.model.dao.WrongAnswerNoteRepository;
import com.wanted.moneybeetle.domain.quiz.model.dto.QuizQuestionDTO;
import com.wanted.moneybeetle.domain.quiz.model.dto.QuizReviewDTO;
import com.wanted.moneybeetle.domain.quiz.model.dto.QuizSubmitDTO;
import com.wanted.moneybeetle.domain.quiz.model.dto.TestQuizResultDTO;
import com.wanted.moneybeetle.domain.quiz.model.dto.WrongNoteDTO;
import com.wanted.moneybeetle.domain.quiz.model.dto.WrongNoteRetryResultDTO;
import com.wanted.moneybeetle.domain.quiz.model.entity.QuizChoice;
import com.wanted.moneybeetle.domain.quiz.model.entity.QuizQuestion;
import com.wanted.moneybeetle.domain.quiz.model.entity.WrongAnswerNote;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   테스트 퀴즈 채점(기록 없음), 퀴즈 서버 채점(70점 통과), 오답 저장
 *   최초 통과 시 학습 완료 처리 + growth.RewardService 에 보상 요청 (한 트랜잭션, NFR-06)
 *  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuizService {

    private final CourseService courseService;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizChoiceRepository quizChoiceRepository;
    private final WrongAnswerNoteRepository wrongAnswerNoteRepository;

    /* ===== 테스트 퀴즈 (#19) ===== */

    /* API 39. 테스트 퀴즈 문제: OX 5문제, 정답은 보내지 않는다 (QST-01, SP-35) */
    public QuizQuestionDTO getTestQuiz(Long courseId) {
        courseService.checkCourseVisible(courseId);

        List<QuizQuestion> questions = findQuestions(courseId, QuizQuestion.QUIZ_TYPE_TEST);
        return QuizQuestionDTO.from(questions, findChoicesByQuestion(questions));
    }

    /* API 40. 테스트 퀴즈 채점: 정답·해설만 돌려주고 아무것도 저장하지 않는다 (QST-01, SP-33) */
    public TestQuizResultDTO gradeTestQuiz(Long courseId, QuizSubmitDTO request) {
        courseService.checkCourseVisible(courseId);

        List<QuizQuestion> questions = findQuestions(courseId, QuizQuestion.QUIZ_TYPE_TEST);
        Map<Long, List<QuizChoice>> choicesByQuestion = findChoicesByQuestion(questions);
        Map<Long, Long> selected = toSelectedMap(request);

        // 이 강의의 테스트 문항만 채점한다. 다른 강의의 문항 ID를 보내도 무시된다
        List<TestQuizResultDTO.ResultItem> results = gradeQuestions(questions, choicesByQuestion, selected);

        int correctCount = (int) results.stream().filter(TestQuizResultDTO.ResultItem::isCorrect).count();
        // 문항이 없으면 열지 않는다. 모든 문항에서 "그 문항의 보기"를 골랐을 때만 영상이 열린다
        boolean allAnswered = !questions.isEmpty()
                && questions.stream().allMatch(question -> isChoiceOf(
                choicesByQuestion.getOrDefault(question.getQuizQuestionId(), List.of()),
                selected.get(question.getQuizQuestionId())));

        return TestQuizResultDTO.of(correctCount, results, allAnswered);
    }

    /* ===== 퀴즈 제출·채점 (#20에서 이 아래에 추가) ===== */

    /* ===== 오답노트 (#21) ===== */

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
        Map<Long, QuizQuestion> questions = quizQuestionRepository.findAllById(
                        notes.getContent().stream().map(WrongAnswerNote::getQuizQuestionId).toList())
                .stream()
                .collect(Collectors.toMap(QuizQuestion::getQuizQuestionId, Function.identity()));
        Map<Long, List<QuizChoice>> choicesByQuestion = findChoicesByQuestion(List.copyOf(questions.values()));

        return notes.map(note -> {
            QuizQuestion question = questions.get(note.getQuizQuestionId());
            List<QuizChoice> choices = choicesByQuestion.getOrDefault(question.getQuizQuestionId(), List.of());
            return WrongNoteDTO.of(note, question, courses.get(question.getCourseId()),
                    findChoiceContent(choices, note.getLastChoiceId()),
                    findChoiceContent(choices, findCorrectChoiceId(choices)));
        });
    }

    /* API 45. 오답 다시 풀기: 내 오답노트에 있는 문제만 채점하고, 기록·보상은 없다 (QST-06) */
    public WrongNoteRetryResultDTO retryWrongNotes(Long memberId, QuizSubmitDTO request) {
        Map<Long, Long> selected = toSelectedMap(request);
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

        return WrongNoteRetryResultDTO.of(gradeQuestions(questions, findChoicesByQuestion(questions), selected));
    }

    /* API 46. 오답 삭제 (실제 삭제). 내 오답만 지워지고, 없거나 남의 것이면 아무 일도 없다 (SP-36) */
    @Transactional
    public void deleteWrongNote(Long memberId, Long wrongNoteId) {
        wrongAnswerNoteRepository.deleteByWrongAnswerNoteIdAndMemberId(wrongNoteId, memberId);
    }

    /* ===== 관리자 검수 (#35, course 도메인 AdminCourseService에서 호출) ===== */

    /* API 90. 검수 상세의 퀴즈: 테스트·본 퀴즈 모든 문항 + 정답 포함 보기. 보기는 한 번에 조회 */
    public List<QuizReviewDTO> findQuizzesForReview(Long courseId) {
        List<QuizQuestion> questions =
                quizQuestionRepository.findByCourseIdOrderByQuizTypeAscSortOrderAscQuizQuestionIdAsc(courseId);
        Map<Long, List<QuizChoice>> choicesByQuestion = findChoicesByQuestion(questions);

        return questions.stream()
                .map(question -> QuizReviewDTO.of(question,
                        choicesByQuestion.getOrDefault(question.getQuizQuestionId(), List.of())))
                .toList();
    }

    /* ===== 내부 도우미 ===== */

    private List<QuizQuestion> findQuestions(Long courseId, String quizType) {
        return quizQuestionRepository.findByCourseIdAndQuizTypeOrderBySortOrderAsc(courseId, quizType);
    }

    /* 문항별 보기를 한 번에 조회해서 문항 ID로 묶는다 */
    private Map<Long, List<QuizChoice>> findChoicesByQuestion(List<QuizQuestion> questions) {
        if (questions.isEmpty()) {
            return Map.of();
        }
        List<Long> questionIds = questions.stream().map(QuizQuestion::getQuizQuestionId).toList();
        return quizChoiceRepository.findByQuizQuestionIdInOrderBySortOrderAsc(questionIds).stream()
                .collect(Collectors.groupingBy(QuizChoice::getQuizQuestionId));
    }

    /* 문항별로 고른 보기가 정답 보기와 같은지 채점한다 (테스트 퀴즈·오답 다시 풀기 공통) */
    private List<TestQuizResultDTO.ResultItem> gradeQuestions(List<QuizQuestion> questions,
                                                              Map<Long, List<QuizChoice>> choicesByQuestion,
                                                              Map<Long, Long> selected) {
        return questions.stream()
                .map(question -> {
                    Long correctChoiceId = findCorrectChoiceId(
                            choicesByQuestion.getOrDefault(question.getQuizQuestionId(), List.of()));
                    boolean correct = correctChoiceId != null
                            && correctChoiceId.equals(selected.get(question.getQuizQuestionId()));
                    return TestQuizResultDTO.ResultItem.of(question, correct, correctChoiceId);
                })
                .toList();
    }

    /* 답안을 "문항 ID → 고른 보기 ID"로 바꾼다.
     * 같은 문항이 여러 번 오면 첫 답만 쓴다. 첫 답이 미응답(choiceId null)이어도 그대로 유지한다 */
    private Map<Long, Long> toSelectedMap(QuizSubmitDTO request) {
        Map<Long, Long> selected = new HashMap<>();
        for (QuizSubmitDTO.AnswerItem answer : request.getAnswers()) {
            if (answer.getQuestionId() != null && !selected.containsKey(answer.getQuestionId())) {
                selected.put(answer.getQuestionId(), answer.getChoiceId());
            }
        }
        return selected;
    }

    /* 고른 보기가 이 문항의 보기 목록에 실제로 있는지. 없는 ID나 다른 문항의 보기 ID면 false */
    private boolean isChoiceOf(List<QuizChoice> choices, Long choiceId) {
        return choiceId != null
                && choices.stream().anyMatch(choice -> choice.getQuizChoiceId().equals(choiceId));
    }

    private Long findCorrectChoiceId(List<QuizChoice> choices) {
        return choices.stream()
                .filter(QuizChoice::isCorrect)
                .map(QuizChoice::getQuizChoiceId)
                .findFirst()
                .orElse(null);
    }

    /* 보기 ID → 보기 내용. 없으면 null */
    private String findChoiceContent(List<QuizChoice> choices, Long choiceId) {
        if (choiceId == null) {
            return null;
        }
        return choices.stream()
                .filter(choice -> choice.getQuizChoiceId().equals(choiceId))
                .map(QuizChoice::getContent)
                .findFirst()
                .orElse(null);
    }
}