package com.wanted.moneybeetle.domain.quiz.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/* comment.
*   담당 : DBA ① (강의·퀴즈)
*   테이블 quiz_question (quiz_type TEST / MAIN)
*  */

@Entity
@Table(name = "quiz_question")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizQuestion {

    public static final String QUIZ_TYPE_TEST = "TEST";       // 영상 전 OX 5문제
    public static final String QUIZ_TYPE_MAIN = "MAIN";       // 영상 후 4지선다
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // TODO 공통 설정(SCM)의 BaseTimeEntity가 생기면 상속으로 바꾸기
    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
