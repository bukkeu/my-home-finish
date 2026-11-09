package com.wanted.moneybeetle.domain.course.controller;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.course.model.dto.AdminCourseDetailDTO;
import com.wanted.moneybeetle.domain.course.model.dto.AdminCourseListItemDTO;
import com.wanted.moneybeetle.domain.course.model.dto.CourseRejectDTO;
import com.wanted.moneybeetle.domain.course.model.dto.CourseReviewResultDTO;
import com.wanted.moneybeetle.domain.course.model.dto.CourseVisibilityDTO;
import com.wanted.moneybeetle.domain.course.model.service.AdminCourseService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import com.wanted.moneybeetle.global.common.PageResponse;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   GET /api/v1/admin/courses, GET /api/v1/admin/courses/{courseId}
 *   POST /api/v1/admin/courses/{courseId}/approve, POST .../reject
 *   PATCH /api/v1/admin/courses/{courseId}/visibility, DELETE /api/v1/admin/courses/{courseId}
 *   권한: SecurityConfig가 /api/v1/admin/** 를 ADMIN만 허용 (로그인 안 함 401, 권한 없음 403)
 *  */
@RestController
@RequestMapping("/api/v1/admin/courses")
@RequiredArgsConstructor
public class AdminCourseController {

    private static final int MAX_PAGE_SIZE = 50;

    private final AdminCourseService adminCourseService;

    @GetMapping
    public ApiResponse<PageResponse<AdminCourseListItemDTO>> list(@RequestParam(required = false) String status,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(adminCourseService.findCourses(status, toPageable(page, size)));
    }

    @GetMapping("/{courseId}")
    public ApiResponse<AdminCourseDetailDTO> detail(@PathVariable Long courseId) {
        return ApiResponse.success(adminCourseService.getCourseDetail(courseId));
    }

    @PostMapping("/{courseId}/approve")
    public ApiResponse<CourseReviewResultDTO> approve(@AuthenticationPrincipal AuthDetails admin,
                                                      @PathVariable Long courseId) {
        return ApiResponse.success(adminCourseService.approve(adminIdOf(admin), courseId));
    }

    @PostMapping("/{courseId}/reject")
    public ApiResponse<CourseReviewResultDTO> reject(@AuthenticationPrincipal AuthDetails admin,
                                                     @PathVariable Long courseId,
                                                     @Valid @RequestBody CourseRejectDTO request) {
        return ApiResponse.success(adminCourseService.reject(adminIdOf(admin), courseId, request.reason()));
    }

    @PatchMapping("/{courseId}/visibility")
    public ResponseEntity<Void> changeVisibility(@PathVariable Long courseId,
                                                 @Valid @RequestBody CourseVisibilityDTO request) {
        adminCourseService.changeVisibility(courseId, request.isPublic());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{courseId}")
    public ResponseEntity<Void> delete(@PathVariable Long courseId) {
        adminCourseService.delete(courseId);
        return ResponseEntity.noContent().build();
    }

    private Pageable toPageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    /* 검수 이력(course_review.admin_id)에 남길 관리자 ID.
     * 폼 로그인이 아닌 방식으로 들어와 AuthDetails가 없으면 안전한 쪽으로 401 */
    private Long adminIdOf(AuthDetails admin) {
        if (admin == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return admin.getMemberId();
    }
}