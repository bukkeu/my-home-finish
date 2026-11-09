package com.wanted.moneybeetle.domain.course.model.service;

import com.wanted.moneybeetle.domain.course.model.dao.CategoryRepository;
import com.wanted.moneybeetle.domain.course.model.dao.CourseRepository;
import com.wanted.moneybeetle.domain.course.model.dto.AdminCategoryTreeDTO;
import com.wanted.moneybeetle.domain.course.model.dto.CategoryCreateDTO;
import com.wanted.moneybeetle.domain.course.model.dto.CategoryUpdateDTO;
import com.wanted.moneybeetle.domain.course.model.entity.Category;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   API 85~88 관리자 카테고리·소주제 관리 (ADM-02)
 *   삭제 규칙 (DB 설계 데이터 처리): 강의(삭제된 강의 포함)나 소주제가 연결돼 있으면 비활성, 없으면 실제 삭제
 *  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminCategoryService {

    private final CategoryRepository categoryRepository;
    private final CourseRepository courseRepository;

    /* API 85 카테고리 → 소주제 트리 (비활성 포함, 강의 수는 한 번에 집계) */
    public List<AdminCategoryTreeDTO> findTree() {
        List<Category> categories = categoryRepository.findAllByOrderByDepthAscSortOrderAscCategoryIdAsc();
        Map<Long, Long> courseCounts = courseRepository.countCoursesByCategory().stream()
                .collect(Collectors.toMap(
                        CourseRepository.CategoryCourseCount::getCategoryId,
                        CourseRepository.CategoryCourseCount::getCourseCount));

        Map<Long, List<Category>> childrenByParent = categories.stream()
                .filter(Category::isSubCategory)
                .collect(Collectors.groupingBy(Category::getParentId));

        return categories.stream()
                .filter(category -> !category.isSubCategory())
                .map(top -> AdminCategoryTreeDTO.of(top,
                        courseCounts.getOrDefault(top.getCategoryId(), 0L),
                        childrenByParent.getOrDefault(top.getCategoryId(), List.of()).stream()
                                .map(sub -> AdminCategoryTreeDTO.of(sub,
                                        courseCounts.getOrDefault(sub.getCategoryId(), 0L), List.of()))
                                .toList()))
                .toList();
    }

    /* API 86 등록. parentId가 있으면 소주제 (2단계까지, Category가 확인) */
    @Transactional
    public Long create(CategoryCreateDTO request) {
        String name = request.name().strip();
        Category category = (request.parentId() == null)
                ? Category.createCategory(name, request.sortOrder())
                : Category.createSubCategory(getCategoryOrThrow(request.parentId()), name, request.sortOrder());
        return categoryRepository.save(category).getCategoryId();
    }

    /* API 87 이름·순서·사용 여부 수정 */
    @Transactional
    public void update(Long categoryId, CategoryUpdateDTO request) {
        getCategoryOrThrow(categoryId).change(request.name().strip(), request.sortOrder(), request.isActive());
    }

    /* API 88 삭제: 연결된 강의·소주제가 있으면 비활성, 없으면 실제 삭제 */
    @Transactional
    public void delete(Long categoryId) {
        Category category = getCategoryOrThrow(categoryId);
        boolean inUse = courseRepository.existsByCategoryId(categoryId)
                || categoryRepository.existsByParentId(categoryId);
        if (inUse) {
            category.deactivate();
            return;
        }
        categoryRepository.delete(category);
    }

    private Category getCategoryOrThrow(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}