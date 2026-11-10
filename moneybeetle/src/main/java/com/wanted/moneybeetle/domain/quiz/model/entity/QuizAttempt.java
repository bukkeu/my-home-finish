package com.wanted.moneybeetle.domain.quiz.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* comment.
*   담당 : DBA ① (강의·퀴즈)
*   테이블 quiz_attempt (MAIN 제출만 저장, 채점 결과·보상 근거라 지우지 않는다 SP-34)
*   - 다른 도메인(member·course)은 ID로만 참조
*  */
@Entity
@Table(name = "quiz_attempt")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long quizAttemptId;

    private Long memberId;
    private Long courseId;
    private int questionCount;
    private int correctCount;
    private int score;              // 0~100 (소수점 이하 버림)
    private boolean isPassed;
    private LocalDateTime submittedAt;

    private QuizAttempt(Long memberId, Long courseId, int questionCount, int correctCount, int score,
                        boolean isPassed, LocalDateTime submittedAt) {
        this.memberId = memberId;
        this.courseId = courseId;
        this.questionCount = questionCount;
        this.correctCount = correctCount;
        this.score = score;
        this.isPassed = isPassed;
        this.submittedAt = submittedAt;
    }

    /* 채점이 끝난 응시를 만든다. 점수는 맞힌 수 / 문항 수 × 100 (소수점 이하 버림), passScore 이상이면 통과 (SP-34) */
    public static QuizAttempt graded(Long memberId, Long courseId, int questionCount, int correctCount,
                                     int passScore, LocalDateTime submittedAt) {
        int score = questionCount == 0 ? 0 : correctCount * 100 / questionCount;
        return new QuizAttempt(memberId, courseId, questionCount, correctCount, score, score >= passScore,
                submittedAt);
    }

    public boolean isOwnedBy(Long memberId) {
        return Objects.equals(this.memberId, memberId);
    }

    public boolean isPerfect() {
        return questionCount > 0 && correctCount == questionCount;
    }
}
