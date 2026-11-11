package com.wanted.moneybeetle.domain.quiz.model.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.wanted.moneybeetle.domain.quiz.model.dto.TestQuizResultDTO;
import com.wanted.moneybeetle.domain.quiz.model.entity.QuizChoice;
import com.wanted.moneybeetle.domain.quiz.model.entity.QuizQuestion;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : DBA ① 신아림 (#294, 퀴즈 채점 규칙)
*   QuestionSheet 를 스프링 없이 new 로 만들어 채점 규칙을 확인한다
*  */
class QuestionSheetTest {

    private static QuizQuestion question(long id) {
        QuizQuestion question = QuizQuestion.create(15L, QuizQuestion.QUIZ_TYPE_MAIN, QuizQuestion.QUESTION_TYPE_CHOICE,
                "문제 " + id, "해설 " + id, (int) id);
        ReflectionTestUtils.setField(question, "quizQuestionId", id);
        return question;
    }

    private static QuizChoice choice(long id, long questionId, boolean correct) {
        QuizChoice choice = QuizChoice.create(questionId, "보기 " + id, correct, (int) (id % 10));
        ReflectionTestUtils.setField(choice, "quizChoiceId", id);
        return choice;
    }

    /* 문항 1: 보기 11(오답) 12(정답) 13(오답) */
    private static QuestionSheet sheet() {
        return new QuestionSheet(question(1), List.of(choice(11, 1, false), choice(12, 1, true), choice(13, 1, false)));
    }

    @Test
    @DisplayName("정답 보기 ID를 찾는다. 정답 보기가 없으면 null")
    void correctChoiceId() {
        assertThat(sheet().correctChoiceId()).isEqualTo(12L);
        assertThat(new QuestionSheet(question(1), List.of(choice(11, 1, false))).correctChoiceId()).isNull();
        assertThat(new QuestionSheet(question(1), List.of()).correctChoiceId()).isNull();
    }

    @Test
    @DisplayName("이 문항의 보기만 owns 가 참이다 (null·없는 ID·다른 문항의 보기는 거짓)")
    void owns() {
        assertThat(sheet().owns(11L)).isTrue();
        assertThat(sheet().owns(12L)).isTrue();
        assertThat(sheet().owns(null)).isFalse();
        assertThat(sheet().owns(99L)).isFalse();
        assertThat(sheet().owns(21L)).isFalse();
    }

    @Test
    @DisplayName("정답 보기를 골랐을 때만 맞음. 오답·미응답·정답이 없는 문항은 틀림")
    void isCorrect() {
        assertThat(sheet().isCorrect(12L)).isTrue();
        assertThat(sheet().isCorrect(11L)).isFalse();
        assertThat(sheet().isCorrect(null)).isFalse();
        assertThat(new QuestionSheet(question(1), List.of(choice(11, 1, false))).isCorrect(null)).isFalse();
    }

    @Test
    @DisplayName("validChoice 는 이 문항의 보기면 그 ID, 아니면 null (답안·오답노트에 남길 값)")
    void validChoice() {
        assertThat(sheet().validChoice(13L)).isEqualTo(13L);
        assertThat(sheet().validChoice(21L)).isNull();
        assertThat(sheet().validChoice(null)).isNull();
    }

    @Test
    @DisplayName("보기 내용은 이 문항의 보기일 때만 돌려준다")
    void contentOf() {
        assertThat(sheet().contentOf(12L)).isEqualTo("보기 12");
        assertThat(sheet().contentOf(21L)).isNull();
        assertThat(sheet().contentOf(null)).isNull();
    }

    @Test
    @DisplayName("of 는 보기를 문항별로 나누고 문항 순서를 지킨다. 보기가 없는 문항은 빈 목록")
    void ofGroupsByQuestion() {
        List<QuestionSheet> sheets = QuestionSheet.of(List.of(question(2), question(1), question(3)),
                List.of(choice(11, 1, true), choice(21, 2, true), choice(22, 2, false)));

        assertThat(sheets).extracting(QuestionSheet::questionId).containsExactly(2L, 1L, 3L);
        assertThat(sheets.get(0).choices()).hasSize(2);
        assertThat(sheets.get(1).choices()).hasSize(1);
        assertThat(sheets.get(2).choices()).isEmpty();
    }

    @Test
    @DisplayName("gradeAll 은 문항마다 고른 보기로 채점하고, 정답 보기 ID와 해설을 함께 돌려준다")
    void gradeAll() {
        List<QuestionSheet> sheets = QuestionSheet.of(List.of(question(1), question(2)),
                List.of(choice(11, 1, true), choice(12, 1, false), choice(21, 2, true), choice(22, 2, false)));

        List<TestQuizResultDTO.ResultItem> results = QuestionSheet.gradeAll(sheets, Map.of(1L, 11L, 2L, 11L));

        assertThat(results).extracting(TestQuizResultDTO.ResultItem::isCorrect).containsExactly(true, false);
        assertThat(results).extracting(TestQuizResultDTO.ResultItem::getCorrectChoiceId).containsExactly(11L, 21L);
        assertThat(results.get(0).getExplanation()).isEqualTo("해설 1");
    }

    @Test
    @DisplayName("보기 목록은 바깥에서 바꿔도 시트에 영향이 없다 (복사해서 갖는다)")
    void choicesCopied() {
        List<QuizChoice> choices = new java.util.ArrayList<>(List.of(choice(11, 1, true)));
        QuestionSheet sheet = new QuestionSheet(question(1), choices);
        choices.clear();

        assertThat(sheet.choices()).hasSize(1);
    }
}
