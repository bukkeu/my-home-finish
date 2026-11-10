package com.wanted.moneybeetle.domain.quiz.model.service;

import com.wanted.moneybeetle.domain.quiz.exception.QuizErrorCode;
import com.wanted.moneybeetle.domain.quiz.model.dao.QuizChoiceRepository;
import com.wanted.moneybeetle.domain.quiz.model.dao.QuizQuestionRepository;
import com.wanted.moneybeetle.domain.quiz.model.dto.QuizCountDTO;
import com.wanted.moneybeetle.domain.quiz.model.dto.QuizSaveDTO;
import com.wanted.moneybeetle.domain.quiz.model.entity.QuizChoice;
import com.wanted.moneybeetle.domain.quiz.model.entity.QuizQuestion;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* comment.
*   담당 : DBA ① (강의·퀴즈)
*   강사 테스트 퀴즈·퀴즈 저장 (API 79·80, TCH-04·05, SP-30, #31)
*   - 본인 강의·수정 가능 상태 확인과 재검수는 course.InstructorCourseService가 먼저 한다 (여기는 quiz 테이블만)
*   - 응시 답안·오답노트가 문항·보기 ID를 가리키므로, 같은 순서의 문항은 지우지 않고 내용만 고친다
*     남는 문항은 지우는데, 푼 기록이 있으면 QUIZ_IN_USE (FK 오류 대신 안내)
*  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstructorQuizService {

    private static final int TEST_QUIZ_COUNT = 5;       // 테스트 퀴즈(OX) 문항 수 (TCH-04)
    private static final int MAIN_QUIZ_MIN = 5;         // 퀴즈(4지선다) 최소 (TCH-05)
    private static final int MAIN_QUIZ_MAX = 15;        // 퀴즈 최대
    private static final int CHOICE_COUNT = 4;          // 4지선다
    private static final int CHOICE_MAX_LENGTH = 200;   // quiz_choice.content
    private static final List<String> OX = List.of("O", "X");

    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizChoiceRepository quizChoiceRepository;

    /* API 79 테스트 퀴즈: OX 정확히 5문제 + 해설 */
    @Transactional
    public QuizCountDTO saveTestQuiz(Long courseId, QuizSaveDTO request) {
        List<QuizSaveDTO.Question> questions = request.questions();
        if (questions.size() != TEST_QUIZ_COUNT) {
            throw new BusinessException(QuizErrorCode.QUIZ_COUNT_INVALID);
        }
        List<NewQuestion> newQuestions = new ArrayList<>();
        for (QuizSaveDTO.Question question : questions) {
            String answer = question.answer() == null ? "" : question.answer().strip().toUpperCase(Locale.ROOT);
            int answerIndex = OX.indexOf(answer);
            if (answerIndex < 0) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT);
            }
            newQuestions.add(new NewQuestion(question.content().strip(), question.explanation().strip(),
                    OX, answerIndex));
        }
        replaceQuestions(courseId, QuizQuestion.QUIZ_TYPE_TEST, QuizQuestion.QUESTION_TYPE_OX, newQuestions);
        return new QuizCountDTO(newQuestions.size());
    }

    /* API 80 퀴즈: 4지선다 5~15문제 + 정답 + 해설 */
    @Transactional
    public QuizCountDTO saveMainQuiz(Long courseId, QuizSaveDTO request) {
        List<QuizSaveDTO.Question> questions = request.questions();
        if (questions.size() < MAIN_QUIZ_MIN || questions.size() > MAIN_QUIZ_MAX) {
            throw new BusinessException(QuizErrorCode.QUIZ_COUNT_INVALID);
        }
        List<NewQuestion> newQuestions = new ArrayList<>();
        for (QuizSaveDTO.Question question : questions) {
            List<String> choices = question.choices();
            Integer answerIndex = question.answerIndex();
            if (choices == null || choices.size() != CHOICE_COUNT
                    || answerIndex == null || answerIndex < 0 || answerIndex >= CHOICE_COUNT) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT);
            }
            List<String> stripped = new ArrayList<>();
            for (String choice : choices) {
                if (choice == null || choice.isBlank() || choice.strip().length() > CHOICE_MAX_LENGTH) {
                    throw new BusinessException(CommonErrorCode.INVALID_INPUT);
                }
                stripped.add(choice.strip());
            }
            newQuestions.add(new NewQuestion(question.content().strip(), question.explanation().strip(),
                    stripped, answerIndex));
        }
        replaceQuestions(courseId, QuizQuestion.QUIZ_TYPE_MAIN, QuizQuestion.QUESTION_TYPE_CHOICE, newQuestions);
        return new QuizCountDTO(newQuestions.size());
    }

    /* ===== 내부 도우미 ===== */

    /* 이 강의·종류의 문항을 요청 내용으로 맞춘다.
     * 1) 같은 순서의 기존 문항은 내용만 고친다 (보기도 같은 순서면 고치고, 개수가 다르면 새로 만든다)
     * 2) 남는 기존 문항은 지운다 (푼 기록이 있으면 QUIZ_IN_USE)
     * 3) 모자란 문항은 새로 만든다 */
    private void replaceQuestions(Long courseId, String quizType, String questionType, List<NewQuestion> newQuestions) {
        List<QuizQuestion> existing = quizQuestionRepository.findByCourseIdAndQuizTypeOrderBySortOrderAsc(courseId, quizType);

        List<QuizQuestion> removed = existing.size() > newQuestions.size()
                ? existing.subList(newQuestions.size(), existing.size()) : List.of();
        if (!removed.isEmpty()) {
            List<Long> removedIds = removed.stream().map(QuizQuestion::getQuizQuestionId).toList();
            if (quizQuestionRepository.countUsages(removedIds) > 0) {
                throw new BusinessException(QuizErrorCode.QUIZ_IN_USE);
            }
            quizChoiceRepository.deleteByQuizQuestionIdIn(removedIds);
            quizChoiceRepository.flush();                       // 보기를 먼저 지워야 문항을 지울 수 있다 (FK)
            quizQuestionRepository.deleteAllInBatch(removed);
        }

        List<QuizQuestion> kept = existing.subList(0, Math.min(existing.size(), newQuestions.size()));
        Map<Long, List<QuizChoice>> choicesByQuestion = kept.isEmpty() ? Map.of()
                : quizChoiceRepository.findByQuizQuestionIdInOrderBySortOrderAsc(
                        kept.stream().map(QuizQuestion::getQuizQuestionId).toList()).stream()
                .collect(Collectors.groupingBy(QuizChoice::getQuizQuestionId));

        for (int i = 0; i < newQuestions.size(); i++) {
            NewQuestion next = newQuestions.get(i);
            int sortOrder = i + 1;
            if (i < kept.size()) {
                QuizQuestion question = kept.get(i);
                question.change(next.content(), next.explanation(), sortOrder);
                updateChoices(question.getQuizQuestionId(),
                        choicesByQuestion.getOrDefault(question.getQuizQuestionId(), List.of()), next);
            } else {
                QuizQuestion question = quizQuestionRepository.save(QuizQuestion.create(
                        courseId, quizType, questionType, next.content(), next.explanation(), sortOrder));
                createChoices(question.getQuizQuestionId(), next);
            }
        }
    }

    /* 보기 개수가 같으면 순서대로 고치고(답안이 보기 ID를 가리킴), 다르면 지우고 새로 만든다 */
    private void updateChoices(Long questionId, List<QuizChoice> current, NewQuestion next) {
        if (current.size() == next.choices().size()) {
            for (int i = 0; i < current.size(); i++) {
                current.get(i).change(next.choices().get(i), i == next.answerIndex());
            }
            return;
        }
        if (quizQuestionRepository.countUsages(List.of(questionId)) > 0) {
            throw new BusinessException(QuizErrorCode.QUIZ_IN_USE);
        }
        quizChoiceRepository.deleteAllInBatch(current);
        createChoices(questionId, next);
    }

    private void createChoices(Long questionId, NewQuestion next) {
        List<QuizChoice> choices = new ArrayList<>();
        for (int i = 0; i < next.choices().size(); i++) {
            choices.add(QuizChoice.create(questionId, next.choices().get(i), i == next.answerIndex(), i + 1));
        }
        quizChoiceRepository.saveAll(choices);
    }

    /* 검사를 마친 문항 하나 (보기 목록 + 정답 위치) */
    private record NewQuestion(String content, String explanation, List<String> choices, int answerIndex) {
    }
}
