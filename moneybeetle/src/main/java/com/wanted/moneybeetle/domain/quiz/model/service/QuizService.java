package com.wanted.moneybeetle.domain.quiz.model.service;

import com.wanted.moneybeetle.domain.course.model.service.CourseService;
import com.wanted.moneybeetle.domain.quiz.model.dao.QuizChoiceRepository;
import com.wanted.moneybeetle.domain.quiz.model.dao.QuizQuestionRepository;
import com.wanted.moneybeetle.domain.quiz.model.dto.QuizQuestionDTO;
import com.wanted.moneybeetle.domain.quiz.model.dto.QuizSubmitDTO;
import com.wanted.moneybeetle.domain.quiz.model.dto.TestQuizResultDTO;
import com.wanted.moneybeetle.domain.quiz.model.entity.QuizChoice;
import com.wanted.moneybeetle.domain.quiz.model.entity.QuizQuestion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        List<TestQuizResultDTO.ResultItem> results = questions.stream()
                .map(question -> {
                    Long correctChoiceId = findCorrectChoiceId(
                            choicesByQuestion.getOrDefault(question.getQuizQuestionId(), List.of()));
                    boolean correct = correctChoiceId != null
                            && correctChoiceId.equals(selected.get(question.getQuizQuestionId()));
                    return TestQuizResultDTO.ResultItem.of(question, correct, correctChoiceId);
                })
                .toList();

        int correctCount = (int) results.stream().filter(TestQuizResultDTO.ResultItem::isCorrect).count();
        // 문항이 없으면 열지 않는다. 모든 문항에 실제로 답(보기 선택)했을 때만 영상이 열린다
        boolean allAnswered = !questions.isEmpty()
                && questions.stream().allMatch(question -> selected.get(question.getQuizQuestionId()) != null);

        return TestQuizResultDTO.of(correctCount, results, allAnswered);
    }

    /* ===== 퀴즈 제출·채점 (#20에서 이 아래에 추가) ===== */

    /* ===== 내부 도우미 ===== */

    private List<QuizQuestion> findQuestions(Long courseId, String quizType) {
        return quizQuestionRepository.findByCourseIdAndQuizTypeOrderBySortOrderAsc(courseId, quizType);
    }

    /* 문항별 보기를 한 번에 조회해서 문항 ID로 묶는다 */
    private Map<Long, List<QuizChoice>> findChoicesByQuestion(List<QuizQuestion> questions) {
        List<Long> questionIds = questions.stream().map(QuizQuestion::getQuizQuestionId).toList();
        return quizChoiceRepository.findByQuizQuestionIdInOrderBySortOrderAsc(questionIds).stream()
                .collect(Collectors.groupingBy(QuizChoice::getQuizQuestionId));
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

    private Long findCorrectChoiceId(List<QuizChoice> choices) {
        return choices.stream()
                .filter(QuizChoice::isCorrect)
                .map(QuizChoice::getQuizChoiceId)
                .findFirst()
                .orElse(null);
    }
}