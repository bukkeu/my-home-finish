package com.wanted.moneybeetle.domain.course.controller;

import com.wanted.moneybeetle.domain.course.model.dto.CategoryTreeDTO;
import com.wanted.moneybeetle.domain.course.model.dto.CompletedCourseDTO;
import com.wanted.moneybeetle.domain.course.model.dto.CourseDetailDTO;
import com.wanted.moneybeetle.domain.course.model.dto.CourseListDTO;
import com.wanted.moneybeetle.domain.course.model.dto.LessonPlayDTO;
import com.wanted.moneybeetle.domain.course.model.dto.ProgressDTO;
import com.wanted.moneybeetle.domain.course.model.dto.ScrapListDTO;
import com.wanted.moneybeetle.domain.course.model.dto.WatchHistoryDTO;
import com.wanted.moneybeetle.domain.course.model.service.CourseService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 *   화면 : /user/lectures, /user/lecture-detail
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
        // TODO 공통 설정(SCM) 병합 후 ApiResponse.success(PageResponse.from(...))로 감싸기
        return courseService.findCourses(currentMemberId(), categoryId, toPageable(page, size, Sort.by("courseId")));
    }

    /* API 31. 강의 상세 */
    @GetMapping("/api/v1/courses/{courseId}")
    @ResponseBody
    public CourseDetailDTO getCourse(@PathVariable Long courseId) {
        // TODO 공통 설정(SCM) 병합 후 ApiResponse.success(...)로 감싸기
        return courseService.getCourseDetail(currentMemberId(), courseId);
    }

    /* ===== 영상 재생·이어보기·시청 기록 (#17) ===== */

    /* API 32. 영상 재생 정보 */
    @GetMapping("/api/v1/courses/{courseId}/lessons/{lessonId}")
    @ResponseBody
    public LessonPlayDTO getLesson(@PathVariable Long courseId, @PathVariable Long lessonId) {
        return courseService.getLessonPlay(currentMemberId(), courseId, lessonId);
    }

    /* API 33. 시청 위치 저장 → 204 No Content */
    @PutMapping("/api/v1/courses/{courseId}/lessons/{lessonId}/progress")
    public ResponseEntity<Void> saveProgress(@PathVariable Long courseId,
                                             @PathVariable Long lessonId,
                                             @Valid @RequestBody ProgressDTO request) {
        courseService.saveProgress(currentMemberId(), courseId, lessonId, request);
        return ResponseEntity.noContent().build();
    }

    /* API 34. 시청 기록 */
    @GetMapping("/api/v1/members/me/watch-histories")
    @ResponseBody
    public Page<WatchHistoryDTO> getWatchHistories(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        return courseService.findWatchHistories(currentMemberId(), toPageable(page, size, Sort.unsorted()));
    }

    /* API 35. 학습 완료 목록 */
    @GetMapping("/api/v1/members/me/completed-courses")
    @ResponseBody
    public Page<CompletedCourseDTO> getCompletedCourses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        return courseService.findCompletedCourses(currentMemberId(), toPageable(page, size, Sort.unsorted()));
    }

    /* ===== 스크랩 (#18) ===== */

    /* API 36. 스크랩 → 201 Created */
    @PostMapping("/api/v1/courses/{courseId}/scrap")
    public ResponseEntity<Void> createScrap(@PathVariable Long courseId) {
        courseService.createScrap(currentMemberId(), courseId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /* API 37. 스크랩 해제 → 204 No Content */
    @DeleteMapping("/api/v1/courses/{courseId}/scrap")
    public ResponseEntity<Void> deleteScrap(@PathVariable Long courseId) {
        courseService.deleteScrap(currentMemberId(), courseId);
        return ResponseEntity.noContent().build();
    }

    /* API 38. 스크랩 목록 */
    @GetMapping("/api/v1/members/me/scraps")
    @ResponseBody
    public Page<ScrapListDTO> getScraps(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        return courseService.findScraps(currentMemberId(), toPageable(page, size, Sort.unsorted()));
    }

    /* ===== 화면 (#52) =====
     * GET /user/lectures, /user/lecture-detail → Thymeleaf 템플릿 반환
     */

    /* page는 0 이상, size는 1~100 사이로 맞춘다 (API 명세서 1-3) */
    private Pageable toPageable(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sort);
    }

    /* 본인 데이터는 주소로 회원 ID를 받지 않고 세션에서 꺼낸다 (NFR-03)
     * 로그인(#4·#6)이 병합되기 전에는 회원을 알 수 없으므로 막는다 (안전한 쪽으로 실패, LevelCheckInterceptor와 같은 방식) */
    private Long currentMemberId() {
        // TODO 로그인 병합 후 @AuthenticationPrincipal AuthDetails의 getMemberId()로 교체
        throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
    }
}
