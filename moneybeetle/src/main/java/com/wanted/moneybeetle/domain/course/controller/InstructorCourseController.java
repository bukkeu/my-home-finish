package com.wanted.moneybeetle.domain.course.controller;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.course.model.dto.CourseEditDTO;
import com.wanted.moneybeetle.domain.course.model.dto.CourseSaveDTO;
import com.wanted.moneybeetle.domain.course.model.dto.CourseStatusDTO;
import com.wanted.moneybeetle.domain.course.model.dto.LessonIdsDTO;
import com.wanted.moneybeetle.domain.course.model.dto.LessonSaveDTO;
import com.wanted.moneybeetle.domain.course.model.dto.MyCourseListDTO;
import com.wanted.moneybeetle.domain.course.model.dto.ThumbnailDTO;
import com.wanted.moneybeetle.domain.course.model.dto.VideoUploadDTO;
import com.wanted.moneybeetle.domain.course.model.service.InstructorCourseService;
import com.wanted.moneybeetle.domain.quiz.model.dto.QuizCountDTO;
import com.wanted.moneybeetle.domain.quiz.model.dto.QuizSaveDTO;
import com.wanted.moneybeetle.global.common.ApiResponse;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/* comment.
*   담당 : DBA ① (강의·퀴즈)
*   GET/POST /api/v1/instructor/courses, GET/PUT/DELETE /api/v1/instructor/courses/{courseId}
*   POST /thumbnail, PUT /lessons, PUT /test-quiz, PUT /quiz, POST /review-request
*   화면 : /instructor/dashboard, /instructor/course-create
*   - 72~77 (#30), 78~80 영상·요약글·퀴즈 저장 + 영상 파일 올리기 (#31), 81 검수 요청 (#32)
*   - 권한: SecurityConfig가 /api/v1/instructor/** 를 승인된 강사(INSTRUCTOR)만 허용. 본인 강의 확인은 서비스가 한다
*  */
@RestController
@RequestMapping("/api/v1/instructor/courses")
@RequiredArgsConstructor
public class InstructorCourseController {

    private final InstructorCourseService instructorCourseService;

    /* API 72 내 강의 목록 (status는 선택) */
    @GetMapping
    public ApiResponse<MyCourseListDTO> list(@AuthenticationPrincipal AuthDetails me,
                                             @RequestParam(required = false) String status) {
        return ApiResponse.success(instructorCourseService.findMyCourses(memberIdOf(me), status));
    }

    /* API 73 강의 생성 → 201 */
    @PostMapping
    public ResponseEntity<ApiResponse<CourseStatusDTO>> create(@AuthenticationPrincipal AuthDetails me,
                                                               @Valid @RequestBody CourseSaveDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(instructorCourseService.createCourse(memberIdOf(me), request)));
    }

    /* API 74 강의 편집 정보 */
    @GetMapping("/{courseId}")
    public ApiResponse<CourseEditDTO> detail(@AuthenticationPrincipal AuthDetails me,
                                             @PathVariable Long courseId) {
        return ApiResponse.success(instructorCourseService.getCourseForEdit(memberIdOf(me), courseId));
    }

    /* API 75 강의 수정 */
    @PutMapping("/{courseId}")
    public ApiResponse<CourseStatusDTO> update(@AuthenticationPrincipal AuthDetails me,
                                               @PathVariable Long courseId,
                                               @Valid @RequestBody CourseSaveDTO request) {
        return ApiResponse.success(instructorCourseService.updateCourse(memberIdOf(me), courseId, request));
    }

    /* API 76 강의 삭제 → 204 */
    @DeleteMapping("/{courseId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthDetails me, @PathVariable Long courseId) {
        instructorCourseService.deleteCourse(memberIdOf(me), courseId);
        return ResponseEntity.noContent().build();
    }

    /* API 77 썸네일 업로드 (multipart/form-data, 필드 이름 file) */
    @PostMapping(value = "/{courseId}/thumbnail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ThumbnailDTO> uploadThumbnail(@AuthenticationPrincipal AuthDetails me,
                                                     @PathVariable Long courseId,
                                                     @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(instructorCourseService.changeThumbnail(memberIdOf(me), courseId, file));
    }

    /* API 78 부속 "영상 파일 올리기" (multipart/form-data, 필드 이름 file, mp4 500MB) */
    @PostMapping(value = "/{courseId}/lessons/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<VideoUploadDTO> uploadVideo(@AuthenticationPrincipal AuthDetails me,
                                                   @PathVariable Long courseId,
                                                   @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(instructorCourseService.uploadVideo(memberIdOf(me), courseId, file));
    }

    /* API 78 영상·요약글 일괄 저장 (Idempotency-Key 헤더, IdempotencyFilter) */
    @PutMapping("/{courseId}/lessons")
    public ApiResponse<LessonIdsDTO> saveLessons(@AuthenticationPrincipal AuthDetails me,
                                                 @PathVariable Long courseId,
                                                 @Valid @RequestBody LessonSaveDTO request) {
        return ApiResponse.success(instructorCourseService.saveLessons(memberIdOf(me), courseId, request));
    }

    /* API 79 테스트 퀴즈 저장 (OX 5문제) */
    @PutMapping("/{courseId}/test-quiz")
    public ApiResponse<QuizCountDTO> saveTestQuiz(@AuthenticationPrincipal AuthDetails me,
                                                  @PathVariable Long courseId,
                                                  @Valid @RequestBody QuizSaveDTO request) {
        return ApiResponse.success(instructorCourseService.saveTestQuiz(memberIdOf(me), courseId, request));
    }

    /* API 80 퀴즈 저장 (4지선다 5~15문제) */
    @PutMapping("/{courseId}/quiz")
    public ApiResponse<QuizCountDTO> saveQuiz(@AuthenticationPrincipal AuthDetails me,
                                              @PathVariable Long courseId,
                                              @Valid @RequestBody QuizSaveDTO request) {
        return ApiResponse.success(instructorCourseService.saveQuiz(memberIdOf(me), courseId, request));
    }

    /* API 81 검수 요청 */
    @PostMapping("/{courseId}/review-request")
    public ApiResponse<CourseStatusDTO> requestReview(@AuthenticationPrincipal AuthDetails me,
                                                      @PathVariable Long courseId) {
        return ApiResponse.success(instructorCourseService.requestReview(memberIdOf(me), courseId));
    }

    /* 로그인 강사 ID. 로그인 정보가 없으면 안전한 쪽으로 401 */
    private Long memberIdOf(AuthDetails me) {
        if (me == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return me.getMemberId();
    }
}
