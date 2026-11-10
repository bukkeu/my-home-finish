package com.wanted.moneybeetle.domain.course.controller;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.course.model.dto.CategoryTreeDTO;
import com.wanted.moneybeetle.domain.course.model.dto.CompletedCourseDTO;
import com.wanted.moneybeetle.domain.course.model.dto.CourseDetailDTO;
import com.wanted.moneybeetle.domain.course.model.dto.CourseListDTO;
import com.wanted.moneybeetle.domain.course.model.dto.LessonPlayDTO;
import com.wanted.moneybeetle.domain.course.model.dto.ProgressDTO;
import com.wanted.moneybeetle.domain.course.model.dto.ScrapListDTO;
import com.wanted.moneybeetle.domain.course.model.dto.WatchHistoryDTO;
import com.wanted.moneybeetle.domain.course.model.service.CourseService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import com.wanted.moneybeetle.global.common.PageResponse;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   GET /api/v1/categories, /courses, /courses/{courseId}, /courses/{courseId}/lessons/{lessonId}
 *   PUT /api/v1/courses/{courseId}/lessons/{lessonId}/progress (이어보기)
 *   POST/DELETE /api/v1/courses/{courseId}/scrap
 *   GET /api/v1/members/me/watch-histories, /completed-courses, /scraps
 *   화면 : /user/lectures (course/category), /user/lecture-detail (course/detail) - #52
 *   로그인 회원: @AuthenticationPrincipal AuthDetails (#132). 주소 단위 로그인 여부는 SecurityConfig가 정한다
 *   응답: 공통 형식 ApiResponse, 목록은 PageResponse { items, page, size, totalElements, totalPages } (API 명세서 1-3, #157)
 *  */
@Controller
@RequiredArgsConstructor
public class CourseController {

    private static final int DEFAULT_PAGE_SIZE = 20;   // API 명세서 1-3
    private static final int MAX_PAGE_SIZE = 100;

    private final CourseService courseService;

    /* ===== 카테고리·강의 조회 (#16) ===== */

    /* API 29. 카테고리·소주제 */
    @GetMapping("/api/v1/categories")
    @ResponseBody
    public ApiResponse<List<CategoryTreeDTO>> getCategories() {
        return ApiResponse.success(courseService.findCategoryTree());
    }

    /* API 30. 강의 목록 (허가 + 공개만) */
    @GetMapping("/api/v1/courses")
    @ResponseBody
    public ApiResponse<PageResponse<CourseListDTO>> getCourses(
            @AuthenticationPrincipal AuthDetails me,
            @RequestParam Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        return ApiResponse.success(PageResponse.from(
                courseService.findCourses(memberIdOrNull(me), categoryId, toPageable(page, size, Sort.by("courseId")))));
    }

    /* API 31. 강의 상세 */
    @GetMapping("/api/v1/courses/{courseId}")
    @ResponseBody
    public ApiResponse<CourseDetailDTO> getCourse(@AuthenticationPrincipal AuthDetails me, @PathVariable Long courseId) {
        return ApiResponse.success(courseService.getCourseDetail(memberIdOrNull(me), courseId));
    }

    /* ===== 영상 재생·이어보기·시청 기록 (#17) ===== */

    /* API 32. 영상 재생 정보 */
    @GetMapping("/api/v1/courses/{courseId}/lessons/{lessonId}")
    @ResponseBody
    public ApiResponse<LessonPlayDTO> getLesson(@AuthenticationPrincipal AuthDetails me,
                                                @PathVariable Long courseId, @PathVariable Long lessonId) {
        return ApiResponse.success(courseService.getLessonPlay(memberIdOf(me), courseId, lessonId));
    }

    /* API 33. 시청 위치 저장 → 204 No Content */
    @PutMapping("/api/v1/courses/{courseId}/lessons/{lessonId}/progress")
    public ResponseEntity<Void> saveProgress(@AuthenticationPrincipal AuthDetails me,
                                             @PathVariable Long courseId,
                                             @PathVariable Long lessonId,
                                             @Valid @RequestBody ProgressDTO request) {
        courseService.saveProgress(memberIdOf(me), courseId, lessonId, request);
        return ResponseEntity.noContent().build();
    }

    /* API 34. 시청 기록 */
    @GetMapping("/api/v1/members/me/watch-histories")
    @ResponseBody
    public ApiResponse<PageResponse<WatchHistoryDTO>> getWatchHistories(
            @AuthenticationPrincipal AuthDetails me,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        return ApiResponse.success(PageResponse.from(
                courseService.findWatchHistories(memberIdOf(me), toPageable(page, size, Sort.unsorted()))));
    }

    /* API 35. 학습 완료 목록 */
    @GetMapping("/api/v1/members/me/completed-courses")
    @ResponseBody
    public ApiResponse<PageResponse<CompletedCourseDTO>> getCompletedCourses(
            @AuthenticationPrincipal AuthDetails me,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        return ApiResponse.success(PageResponse.from(
                courseService.findCompletedCourses(memberIdOf(me), toPageable(page, size, Sort.unsorted()))));
    }

    /* ===== 스크랩 (#18) ===== */

    /* API 36. 스크랩 → 201 Created */
    @PostMapping("/api/v1/courses/{courseId}/scrap")
    public ResponseEntity<Void> createScrap(@AuthenticationPrincipal AuthDetails me, @PathVariable Long courseId) {
        courseService.createScrap(memberIdOf(me), courseId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /* API 37. 스크랩 해제 → 204 No Content */
    @DeleteMapping("/api/v1/courses/{courseId}/scrap")
    public ResponseEntity<Void> deleteScrap(@AuthenticationPrincipal AuthDetails me, @PathVariable Long courseId) {
        courseService.deleteScrap(memberIdOf(me), courseId);
        return ResponseEntity.noContent().build();
    }

    /* API 38. 스크랩 목록 */
    @GetMapping("/api/v1/members/me/scraps")
    @ResponseBody
    public ApiResponse<PageResponse<ScrapListDTO>> getScraps(
            @AuthenticationPrincipal AuthDetails me,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        return ApiResponse.success(PageResponse.from(
                courseService.findScraps(memberIdOf(me), toPageable(page, size, Sort.unsorted()))));
    }

    /* ===== 화면 (#52) =====
     * 화면은 틀만 그리고, 데이터는 화면 스크립트(static/js/course-*.js)가 위 API로 받아 온다
     */

    /* 강의 목록 화면: /user/lectures?cat=money|estate (화면설계 5.2.3) */
    @GetMapping("/user/lectures")
    public String lecturesPage() {
        return "course/category";
    }

    /* 강의 상세·영상 시청 화면: /user/lecture-detail?courseId= (화면설계 5.2.4) */
    @GetMapping("/user/lecture-detail")
    public String lectureDetailPage() {
        return "course/detail";
    }

    /* page는 0 이상, size는 1~100 사이로 맞춘다 (API 명세서 1-3) */
    private Pageable toPageable(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sort);
    }

    /* 비회원도 보는 API(강의 목록·상세)용: 로그인 안 했으면 null → 스크랩 안 한 것으로 표시 */
    private Long memberIdOrNull(AuthDetails me) {
        return me == null ? null : me.getMemberId();
    }

    /* 본인 데이터는 주소로 회원 ID를 받지 않고 세션에서 꺼낸다 (NFR-03). 로그인 정보가 없으면 401 */
    private Long memberIdOf(AuthDetails me) {
        if (me == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return me.getMemberId();
    }
}