package com.wanted.moneybeetle.domain.quiz.controller;

import com.wanted.moneybeetle.domain.quiz.model.dto.QuizSubmitDTO;
import com.wanted.moneybeetle.domain.quiz.model.dto.QuizQuestionDTO;
import com.wanted.moneybeetle.domain.quiz.model.dto.TestQuizResultDTO;
import com.wanted.moneybeetle.domain.quiz.model.service.QuizService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
/* comment.
*   담당 : DBA ① (강의·퀴즈)
*   GET  /api/v1/courses/{courseId}/test-quiz, POST /test-quiz/grade
*   GET  /api/v1/courses/{courseId}/quiz, POST /quiz/attempts, GET /api/v1/quiz-attempts/{attemptId}
*   GET/DELETE /api/v1/members/me/wrong-notes, POST /wrong-notes/retry
*  */
@Controller
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    /* ===== 테스트 퀴즈 (#19) ===== */

    /* API 39. 테스트 퀴즈 문제 */
    @GetMapping("/api/v1/courses/{courseId}/test-quiz")
    @ResponseBody
    public ApiResponse<QuizQuestionDTO> getTestQuiz(@PathVariable Long courseId) {
        return ApiResponse.success(quizService.getTestQuiz(courseId));
    }

    /* API 40. 테스트 퀴즈 채점 (기록하지 않으므로 200) */
    @PostMapping("/api/v1/courses/{courseId}/test-quiz/grade")
    @ResponseBody
    public ApiResponse<TestQuizResultDTO> gradeTestQuiz(@PathVariable Long courseId,
                                                        @Valid @RequestBody QuizSubmitDTO request) {
        return ApiResponse.success(quizService.gradeTestQuiz(courseId, request));
    }

    /* ===== 퀴즈 제출·채점 (#20) =====
     * GET  /api/v1/courses/{courseId}/quiz
     * POST /api/v1/courses/{courseId}/quiz/submit
     */
}