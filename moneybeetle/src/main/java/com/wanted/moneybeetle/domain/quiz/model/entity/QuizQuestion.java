package com.wanted.moneybeetle.domain.quiz.model.entity;

import com.wanted.moneybeetle.global.common.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* comment.
*   담당 : DBA ① (강의·퀴즈)
*   테이블 quiz_question (quiz_type TEST / MAIN)
*   created_at·updated_at 은 BaseTimeEntity(JPA 감사, 서울 시간)가 채운다 (#157)
*  */

@Entity
@Table(name = "quiz_question")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizQuestion extends BaseTimeEntity {

    public static final String QUIZ_TYPE_TEST = "TEST";       // 영상 전 OX 5문제
    public static final String QUIZ_TYPE_MAIN = "MAIN";       // 영상 후 4지선다
    private static final String RETIRED_SUFFIX = "_OLD";      // 보관한 문항: TEST_OLD / MAIN_OLD (#31)
    public static final String QUESTION_TYPE_OX = "OX";
    public static final String QUESTION_TYPE_CHOICE = "CHOICE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long quizQuestionId;

    private Long courseId;
    private String quizType;
    private String questionType;
    private String content;
    private String explanation;
    private int sortOrder;

    /* 강사가 새 문항을 만든다 (API 79·80). 개수·형식 검사는 서비스가 끝낸 뒤 부른다 */
    public static QuizQuestion create(Long courseId, String quizType, String questionType,
                                      String content, String explanation, int sortOrder) {
        QuizQuestion question = new QuizQuestion();
        question.courseId = courseId;
        question.quizType = quizType;
        question.questionType = questionType;
        question.change(content, explanation, sortOrder);
        return question;
    }

    /* 푼 기록이 있는 문항을 강사가 바꾸거나 뺄 때 보관한다 (API 79·80, #31).
     * 퀴즈 종류가 TEST_OLD / MAIN_OLD 가 되어 퀴즈·검수·편집 목록에서는 빠지고,
     * 오답노트·응시 결과는 이 문항(수정 전 문제·보기)을 계속 가리킨다 */
    public void retire() {
        if (!quizType.endsWith(RETIRED_SUFFIX)) {
            this.quizType = quizType + RETIRED_SUFFIX;
        }
    }

    /* 문제·해설·순서 바꾸기 (API 79·80). 푼 기록이 없는 문항이거나, 기록이 있어도 해설·순서만 바꿀 때 */
    public void change(String content, String explanation, int sortOrder) {
        this.content = content;
        this.explanation = explanation;
        this.sortOrder = sortOrder;
    }
}
