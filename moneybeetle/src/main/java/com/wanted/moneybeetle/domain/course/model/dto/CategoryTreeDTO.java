package com.wanted.moneybeetle.domain.course.model.dto;

import com.wanted.moneybeetle.domain.course.model.entity.Category;
import lombok.Getter;

import java.util.List;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   카테고리 → 소주제 트리 (API 29)
 *  */
@Getter
public class CategoryTreeDTO {

    private Long categoryId;
    private String name;
    private List<CategoryTreeDTO> children;

    private CategoryTreeDTO() {
    }

    public static CategoryTreeDTO from(Category category, List<Category> children) {
        CategoryTreeDTO dto = new CategoryTreeDTO();
        dto.categoryId = category.getCategoryId();
        dto.name = category.getName();
        dto.children = children.stream()
                .map(child -> CategoryTreeDTO.from(child, List.of()))
                .toList();
        return dto;
    }
}