package com.wanted.moneybeetle.domain.quiz.model.service;

import com.wanted.moneybeetle.domain.quiz.exception.QuizErrorCode;
import com.wanted.moneybeetle.domain.quiz.model.dao.QuizChoiceRepository;
import com.wanted.moneybeetle.domain.quiz.model.dao.QuizQuestionRepository;
import com.wanted.moneybeetle.domain.quiz.model.dto.CourseQuizStatDTO;
import com.wanted.moneybeetle.domain.quiz.model.dto.QuizCountDTO;
import com.wanted.moneybeetle.domain.quiz.model.dto.QuizSaveDTO;
import com.wanted.moneybeetle.domain.quiz.model.entity.QuizChoice;
import com.wanted.moneybeetle.domain.quiz.model.entity.QuizQuestion;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.nio.charset.StandardCharsets;
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
*   - 응시 답안·오답노트가 문항·보기 ID를 가리키므로, 푼 기록이 있는 문항은 고치거나 지우지 않고 보관한다
*     (quiz_type TEST_OLD / MAIN_OLD → 퀴즈·검수·편집 화면에서는 빠지고, 오답노트·응시 결과는 수정 전 내용 그대로)
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
    private static final int EXPLANATION_MAX_BYTES = 65_535;   // quiz_question.explanation (MySQL TEXT, UTF-8 바이트)
    private static final List<String> OX = List.of("O", "X");

    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizChoiceRepository quizChoiceRepository;

    /* 강사 센터 퀴즈 목록 (API 150, #366): 강의마다 지금 쓰는 문항 수. 문항이 있는 강의만 돌려준다 (course.InstructorContentService 가 부른다) */
    public List<CourseQuizStatDTO> findQuizStats(List<Long> courseIds) {
        if (courseIds.isEmpty()) {
            return List.of();
        }
        return quizQuestionRepository.findQuizStatsByCourseIds(courseIds);
    }

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
            newQuestions.add(new NewQuestion(question.content().strip(), explanationOf(question),
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
            newQuestions.add(new NewQuestion(question.content().strip(), explanationOf(question),
                    stripped, answerIndex));
        }
        replaceQuestions(courseId, QuizQuestion.QUIZ_TYPE_MAIN, QuizQuestion.QUESTION_TYPE_CHOICE, newQuestions);
        return new QuizCountDTO(newQuestions.size());
    }

    /* ===== 내부 도우미 ===== */

    /* 해설은 TEXT 칸이라 글자 수가 아니라 UTF-8 바이트(65,535)로 한도를 본다. 넘으면 DB 오류 대신 입력 오류 */
    private String explanationOf(QuizSaveDTO.Question question) {
        String explanation = question.explanation().strip();
        if (explanation.getBytes(StandardCharsets.UTF_8).length > EXPLANATION_MAX_BYTES) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return explanation;
    }

    /* 이 강의·종류의 문항을 요청 내용으로 맞춘다 (같은 순서끼리 비교).
     * - 푼 기록(퀴즈 답안·오답노트)이 없는 문항: 그대로 고친다 (ID 유지)
     * - 푼 기록이 있는 문항: 문제·보기·정답이 바뀌면 지우지도 고치지도 않고 보관(retire)한 뒤 새 문항을 만든다
     *   → 학습자의 오답노트·응시 결과는 계속 수정 전 문항·보기를 가리킨다. 해설만 바뀌면 그대로 고친다
     * - 남는 기존 문항: 기록이 없으면 지우고, 있으면 보관한다
     * - 모자란 문항: 새로 만든다 */
    private void replaceQuestions(Long courseId, String quizType, String questionType, List<NewQuestion> newQuestions) {
        List<QuizQuestion> existing = quizQuestionRepository.findByCourseIdAndQuizTypeOrderBySortOrderAsc(courseId, quizType);
        Map<Long, List<QuizChoice>> choicesByQuestion = existing.isEmpty() ? Map.of()
                : quizChoiceRepository.findByQuizQuestionIdInOrderBySortOrderAsc(
                        existing.stream().map(QuizQuestion::getQuizQuestionId).toList()).stream()
                .collect(Collectors.groupingBy(QuizChoice::getQuizQuestionId));

        List<QuizQuestion> toDelete = new ArrayList<>();
        for (int i = newQuestions.size(); i < existing.size(); i++) {
            QuizQuestion question = existing.get(i);
            if (hasHistory(question)) {
                question.retire();
            } else {
                toDelete.add(question);
            }
        }
        deleteQuestions(toDelete);

        for (int i = 0; i < newQuestions.size(); i++) {
            NewQuestion next = newQuestions.get(i);
            int sortOrder = i + 1;
            if (i < existing.size()) {
                QuizQuestion question = existing.get(i);
                List<QuizChoice> current = choicesByQuestion.getOrDefault(question.getQuizQuestionId(), List.of());
                if (!hasHistory(question)) {
                    question.change(next.content(), next.explanation(), sortOrder);
                    updateChoices(question.getQuizQuestionId(), current, next);
                    continue;
                }
                if (sameQuestion(question, current, next)) {
                    question.change(next.content(), next.explanation(), sortOrder);   // 해설·순서만 바뀜
                    continue;
                }
                question.retire();
            }
            QuizQuestion created = quizQuestionRepository.save(QuizQuestion.create(
                    courseId, quizType, questionType, next.content(), next.explanation(), sortOrder));
            createChoices(created.getQuizQuestionId(), next);
        }
    }

    /* 학습자가 푼 기록(퀴즈 답안·오답노트)이 이 문항을 가리키는지 */
    private boolean hasHistory(QuizQuestion question) {
        return quizQuestionRepository.countUsages(List.of(question.getQuizQuestionId())) > 0;
    }

    /* 문제·보기·정답이 그대로인지 (학습자가 본 내용이 바뀌지 않는지) */
    private boolean sameQuestion(QuizQuestion question, List<QuizChoice> current, NewQuestion next) {
        if (!question.getContent().equals(next.content()) || current.size() != next.choices().size()) {
            return false;
        }
        for (int i = 0; i < current.size(); i++) {
            QuizChoice choice = current.get(i);
            if (!choice.getContent().equals(next.choices().get(i)) || choice.isCorrect() != (i == next.answerIndex())) {
                return false;
            }
        }
        return true;
    }

    /* 기록이 없는 문항만 지운다. 보기를 먼저 지워야 문항을 지울 수 있다 (FK) */
    private void deleteQuestions(List<QuizQuestion> questions) {
        if (questions.isEmpty()) {
            return;
        }
        quizChoiceRepository.deleteByQuizQuestionIdIn(questions.stream().map(QuizQuestion::getQuizQuestionId).toList());
        quizChoiceRepository.flush();
        quizQuestionRepository.deleteAllInBatch(questions);
    }

    /* 기록이 없는 문항의 보기: 개수가 같으면 순서대로 고치고, 다르면 지우고 새로 만든다 */
    private void updateChoices(Long questionId, List<QuizChoice> current, NewQuestion next) {
        if (current.size() == next.choices().size()) {
            for (int i = 0; i < current.size(); i++) {
                current.get(i).change(next.choices().get(i), i == next.answerIndex());
            }
            return;
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
