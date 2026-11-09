package com.wanted.moneybeetle.domain.quiz.controller;

import com.wanted.moneybeetle.domain.quiz.model.dto.QuizQuestionDTO;
import com.wanted.moneybeetle.domain.quiz.model.dto.QuizSubmitDTO;
import com.wanted.moneybeetle.domain.quiz.model.dto.TestQuizResultDTO;
import com.wanted.moneybeetle.domain.quiz.model.dto.WrongNoteDTO;
import com.wanted.moneybeetle.domain.quiz.model.dto.WrongNoteRetryResultDTO;
import com.wanted.moneybeetle.domain.quiz.model.service.QuizService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import com.wanted.moneybeetle.global.common.PageResponse;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
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

    private static final int DEFAULT_PAGE_SIZE = 20;   // API 명세서 1-3
    private static final int MAX_PAGE_SIZE = 100;

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
     * POST /api/v1/courses/{courseId}/quiz/attempts
     * GET  /api/v1/quiz-attempts/{attemptId}
     */

    /* ===== 오답노트 (#21) ===== */

    /* API 44. 오답노트 (categoryId·courseId는 선택) */
    @GetMapping("/api/v1/members/me/wrong-notes")
    @ResponseBody
    public ApiResponse<PageResponse<WrongNoteDTO>> getWrongNotes(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        return ApiResponse.success(PageResponse.from(
                quizService.findWrongNotes(currentMemberId(), categoryId, courseId, toPageable(page, size))));
    }

    /* API 45. 오답 다시 풀기 채점 (기록하지 않으므로 200) */
    @PostMapping("/api/v1/members/me/wrong-notes/retry")
    @ResponseBody
    public ApiResponse<WrongNoteRetryResultDTO> retryWrongNotes(@Valid @RequestBody QuizSubmitDTO request) {
        return ApiResponse.success(quizService.retryWrongNotes(currentMemberId(), request));
    }

    /* API 46. 오답 삭제 → 204 No Content */
    @DeleteMapping("/api/v1/members/me/wrong-notes/{wrongNoteId}")
    public ResponseEntity<Void> deleteWrongNote(@PathVariable Long wrongNoteId) {
        quizService.deleteWrongNote(currentMemberId(), wrongNoteId);
        return ResponseEntity.noContent().build();
    }

    /* page는 0 이상, size는 1~100. 정렬은 쿼리의 ORDER BY가 정한다 */
    private Pageable toPageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    /* 본인 데이터는 주소로 회원 ID를 받지 않고 세션에서 꺼낸다 (NFR-03)
     * 로그인(#4·#6)이 병합되기 전에는 회원을 알 수 없으므로 막는다 (안전한 쪽으로 실패, #132에서 연결) */
    private Long currentMemberId() {
        // TODO 로그인 병합 후 @AuthenticationPrincipal AuthDetails의 getMemberId()로 교체 (#132)
        throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
    }
}