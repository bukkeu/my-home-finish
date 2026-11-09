package com.wanted.moneybeetle.domain.course.model.dto;

import com.wanted.moneybeetle.domain.course.model.entity.Category;

import java.util.List;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   API 85 관리자 카테고리 트리 (비활성 포함). 소주제의 children은 빈 목록
 *  */
public record AdminCategoryTreeDTO(
        Long categoryId,
        String name,
        int depth,
        int sortOrder,
        boolean isActive,
        long courseCount,
        List<AdminCategoryTreeDTO> children
) {
    public static AdminCategoryTreeDTO of(Category category, long courseCount, List<AdminCategoryTreeDTO> children) {
        return new AdminCategoryTreeDTO(category.getCategoryId(), category.getName(), category.getDepth(),
                category.getSortOrder(), category.isActive(), courseCount, children);
    }
}