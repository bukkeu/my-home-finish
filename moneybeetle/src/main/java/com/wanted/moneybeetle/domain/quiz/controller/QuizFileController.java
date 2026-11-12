package com.wanted.moneybeetle.domain.quiz.controller;

import com.wanted.moneybeetle.domain.quiz.model.dto.QuizFileType;
import com.wanted.moneybeetle.domain.quiz.model.dto.QuizSaveDTO;
import com.wanted.moneybeetle.domain.quiz.model.service.QuizFileService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/* comment.
*   담당 : 서수영 (#368, quiz 도메인 담당 신아림이 나눠 준 작업)
*   POST /api/v1/instructor/quiz-files/parse (API 153) - 퀴즈 문제 파일을 읽어 문항 목록을 돌려준다. 저장하지 않는다
*   화면 : /instructor/course-create (테스트 퀴즈·퀴즈 "문제 파일 올리기")
*   - 권한 : SecurityConfig 가 /api/v1/instructor/** 를 승인된 강사(INSTRUCTOR)만 허용. 데이터를 바꾸지 않지만 POST 라 CSRF 토큰이 필요하다
*   - 요청 : multipart/form-data, file(csv·xlsx·txt, 1MB) + type(TEST | MAIN)
*   - 파일은 수업(08 chap06)처럼 @RequestParam 으로 받는다. 빠졌을 때 400(QUIZ_FILE_INVALID)으로 알려 주려고 required = false 로 받고
*     서비스가 확인한다 (필수로 두면 빠진 요청이 500 이 된다). consumes 를 두지 않는 것도 같은 이유다 (JSON 으로 보내면 파일 없음 400)
*  */
@RestController
@RequestMapping("/api/v1/instructor/quiz-files")
@RequiredArgsConstructor
public class QuizFileController {

    private final QuizFileService quizFileService;

    /* API 153 퀴즈 문제 파일 읽기 */
    @PostMapping("/parse")
    public ApiResponse<QuizSaveDTO> parse(@RequestParam(value = "file", required = false) MultipartFile file,
                                          @RequestParam QuizFileType type) {
        return ApiResponse.success(quizFileService.parse(file, type));
    }
}
