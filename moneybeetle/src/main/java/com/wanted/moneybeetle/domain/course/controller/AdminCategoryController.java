package com.wanted.moneybeetle.domain.course.controller;

import com.wanted.moneybeetle.domain.course.model.dto.AdminCategoryTreeDTO;
import com.wanted.moneybeetle.domain.course.model.dto.CategoryCreateDTO;
import com.wanted.moneybeetle.domain.course.model.dto.CategoryCreatedDTO;
import com.wanted.moneybeetle.domain.course.model.dto.CategoryUpdateDTO;
import com.wanted.moneybeetle.domain.course.model.service.AdminCategoryService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   GET/POST /api/v1/admin/categories, PUT/DELETE /api/v1/admin/categories/{categoryId} (#34, ADM-02)
 *   권한: SecurityConfig가 /api/v1/admin/** 를 ADMIN만 허용 (로그인 안 함 401, 권한 없음 403)
 *  */
@RestController
@RequestMapping("/api/v1/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final AdminCategoryService adminCategoryService;

    @GetMapping
    public ApiResponse<List<AdminCategoryTreeDTO>> tree() {
        return ApiResponse.success(adminCategoryService.findTree());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CategoryCreatedDTO>> create(@Valid @RequestBody CategoryCreateDTO request) {
        Long categoryId = adminCategoryService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(new CategoryCreatedDTO(categoryId)));
    }

    @PutMapping("/{categoryId}")
    public ResponseEntity<Void> update(@PathVariable Long categoryId,
                                       @Valid @RequestBody CategoryUpdateDTO request) {
        adminCategoryService.update(categoryId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> delete(@PathVariable Long categoryId) {
        adminCategoryService.delete(categoryId);
        return ResponseEntity.noContent().build();
    }
}