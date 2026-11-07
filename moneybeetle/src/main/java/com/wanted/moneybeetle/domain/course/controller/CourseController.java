package com.wanted.moneybeetle.domain.course.controller;

import com.wanted.moneybeetle.domain.course.model.dto.CategoryTreeDTO;
import com.wanted.moneybeetle.domain.course.model.dto.CourseDetailDTO;
import com.wanted.moneybeetle.domain.course.model.dto.CourseListDTO;
import com.wanted.moneybeetle.domain.course.model.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   GET /api/v1/categories, /courses, /courses/{courseId}, /courses/{courseId}/lessons/{lessonId}
 *   PUT /api/v1/courses/{courseId}/lessons/{lessonId}/progress (이어보기)
 *   POST/DELETE /api/v1/courses/{courseId}/scrap
 *   GET /api/v1/members/me/watch-histories, /completed-courses, /scraps
 *   화면 : /user/lectures, /user/lecture-detail
 *  */
@Controller
@RequiredArgsConstructor
public class CourseController {

    private static final int DEFAULT_PAGE_SIZE = 20;   // API 명세서 1-3
    private static final int MAX_PAGE_SIZE = 100;

    private final CourseService courseService;

    /* ===== 카테고리·강의 조회 (이번 이슈) ===== */

    /* API 29. 카테고리·소주제 */
    @GetMapping("/api/v1/categories")
    @ResponseBody
    public List<CategoryTreeDTO> getCategories() {
        // TODO 공통 설정(SCM) 병합 후 ApiResponse.success(...)로 감싸기
        return courseService.findCategoryTree();
    }

    /* API 30. 강의 목록 (허가 + 공개만) */
    @GetMapping("/api/v1/courses")
    @ResponseBody
    public Page<CourseListDTO> getCourses(
            @RequestParam Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("courseId"));

        // TODO 공통 설정(SCM) 병합 후 ApiResponse.success(PageResponse.from(...))로 감싸기
        return courseService.findCourses(categoryId, pageable);
    }

    /* API 31. 강의 상세 */
    @GetMapping("/api/v1/courses/{courseId}")
    @ResponseBody
    public CourseDetailDTO getCourse(@PathVariable Long courseId) {
        // TODO 공통 설정(SCM) 병합 후 ApiResponse.success(...)로 감싸기
        return courseService.getCourseDetail(courseId);
    }

    /* ===== 영상 재생·이어보기·시청 기록 (다음 이슈) =====
     * GET /api/v1/courses/{courseId}/lessons/{lessonId}
     * PUT /api/v1/courses/{courseId}/lessons/{lessonId}/progress
     * GET /api/v1/members/me/watch-histories, /completed-courses
     */

    /* ===== 스크랩 (그다음 이슈) =====
     * POST/DELETE /api/v1/courses/{courseId}/scrap
     * GET /api/v1/members/me/scraps
     */

    /* ===== 화면 (화면 이슈) =====
     * GET /user/lectures, /user/lecture-detail → Thymeleaf 템플릿 반환
     */
}