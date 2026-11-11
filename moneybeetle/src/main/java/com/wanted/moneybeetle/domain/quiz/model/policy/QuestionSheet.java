package com.wanted.moneybeetle.domain.quiz.model.policy;

import com.wanted.moneybeetle.domain.quiz.model.dto.TestQuizResultDTO;
import com.wanted.moneybeetle.domain.quiz.model.entity.QuizChoice;
import com.wanted.moneybeetle.domain.quiz.model.entity.QuizQuestion;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/* comment.
 *   담당 : DBA ① 신아림 (#294, 퀴즈 채점 규칙)
 *   문항 1개 + 그 문항의 보기들. 채점 규칙(정답 찾기, 보기 소속 확인, 맞았는지)을 한곳에 모은다.
 *   스프링 빈이 아니라 값 객체라서 new 로 만들어 바로 검증할 수 있다 (QuestionSheetTest)
 *   - correctChoiceId() : 정답 보기 ID. 정답 보기가 없으면 null
 *   - owns(choiceId)    : 그 보기가 이 문항의 보기인지. null·없는 ID·다른 문항의 보기면 false
 *   - isCorrect(choiceId): 정답 보기를 골랐는지. 정답이 없거나 안 골랐으면 false
 *   - validChoice(choiceId): 이 문항의 보기면 그 ID, 아니면 null (다른 문항의 보기 ID가 답안·오답노트에 저장되지 않게)
 *   테스트 퀴즈 채점·본 퀴즈 채점·오답 다시 풀기가 같은 규칙을 쓴다 (QuizService, WrongNoteService)
 *  */
public record QuestionSheet(QuizQuestion question, List<QuizChoice> choices) {

    public QuestionSheet {
        Objects.requireNonNull(question, "question");
        choices = choices == null ? List.of() : List.copyOf(choices);
    }

    /* 문항들과 (여러 문항의) 보기 목록으로 문항마다 시트를 만든다. 문항 순서를 그대로 지킨다 */
    public static List<QuestionSheet> of(List<QuizQuestion> questions, List<QuizChoice> choices) {
        Map<Long, List<QuizChoice>> byQuestion = choices.stream()
                .collect(Collectors.groupingBy(QuizChoice::getQuizQuestionId));
        return questions.stream()
                .map(question -> new QuestionSheet(question,
                        byQuestion.getOrDefault(question.getQuizQuestionId(), List.of())))
                .toList();
    }

    /* 문항마다 고른 보기(문항 ID → 보기 ID)로 채점한다. 결과는 시트 순서대로 */
    public static List<TestQuizResultDTO.ResultItem> gradeAll(List<QuestionSheet> sheets, Map<Long, Long> selected) {
        return sheets.stream()
                .map(sheet -> sheet.grade(selected.get(sheet.questionId())))
                .toList();
    }

    public Long questionId() {
        return question.getQuizQuestionId();
    }

    public Long correctChoiceId() {
        return choices.stream()
                .filter(QuizChoice::isCorrect)
                .map(QuizChoice::getQuizChoiceId)
                .findFirst()
                .orElse(null);
    }

    public boolean owns(Long choiceId) {
        return choiceId != null
                && choices.stream().anyMatch(choice -> choice.getQuizChoiceId().equals(choiceId));
    }

    public boolean isCorrect(Long selectedChoiceId) {
        Long correct = correctChoiceId();
        return correct != null && correct.equals(selectedChoiceId);
    }

    public Long validChoice(Long choiceId) {
        return owns(choiceId) ? choiceId : null;
    }

    /* 보기 ID → 보기 내용. 이 문항의 보기가 아니거나 null 이면 null */
    public String contentOf(Long choiceId) {
        if (choiceId == null) {
            return null;
        }
        return choices.stream()
                .filter(choice -> choice.getQuizChoiceId().equals(choiceId))
                .map(QuizChoice::getContent)
                .findFirst()
                .orElse(null);
    }

    /* 이 문항의 채점 결과 (맞았는지, 정답 보기 ID, 해설) */
    public TestQuizResultDTO.ResultItem grade(Long selectedChoiceId) {
        return TestQuizResultDTO.ResultItem.of(question, isCorrect(selectedChoiceId), correctChoiceId());
    }
}
