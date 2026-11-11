package com.wanted.moneybeetle.domain.course.model.service;

import com.wanted.moneybeetle.domain.course.exception.CourseErrorCode;
import com.wanted.moneybeetle.domain.course.model.dao.CategoryRepository;
import com.wanted.moneybeetle.domain.course.model.dao.CourseRepository;
import com.wanted.moneybeetle.domain.course.model.dto.AdminCategoryTreeDTO;
import com.wanted.moneybeetle.domain.course.model.dto.CategoryCreateDTO;
import com.wanted.moneybeetle.domain.course.model.dto.CategoryUpdateDTO;
import com.wanted.moneybeetle.domain.course.model.entity.Category;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import com.wanted.moneybeetle.global.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   API 85~88 관리자 카테고리·소주제 관리 (ADM-02)
 *   삭제 규칙 (DB 설계 데이터 처리): 강의(삭제된 강의 포함)나 소주제가 연결돼 있으면 비활성, 없으면 실제 삭제
 *   등록·수정·삭제하면 카테고리 표시 이름 캐시(CategoryPathService)를 비운다. 커밋된 뒤에 비워진다 (CacheConfig, #296)
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
    @CacheEvict(cacheNames = CacheConfig.CATEGORY_PATHS, allEntries = true)
    public Long create(CategoryCreateDTO request) {
        String name = request.name().strip();
        Category category = (request.parentId() == null)
                ? Category.createCategory(name, request.sortOrder())
                : Category.createSubCategory(getCategoryOrThrow(request.parentId()), name, request.sortOrder());
        return categoryRepository.save(category).getCategoryId();
    }

    /* API 87 이름·순서·사용 여부 수정 */
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CATEGORY_PATHS, allEntries = true)
    public void update(Long categoryId, CategoryUpdateDTO request) {
        getCategoryOrThrow(categoryId).change(request.name().strip(), request.sortOrder(), request.isActive());
    }

    /* API 88 삭제: 연결된 강의·소주제가 있으면 비활성, 없으면 실제 삭제
     * 확인과 삭제 사이에 강의가 연결되면 FK 위반이 난다 → 500 대신 409 CATEGORY_IN_USE로 알리고 트랜잭션은 롤백.
     * (예외 뒤 같은 세션에서 비활성으로 바꾸면 Hibernate 세션이 깨질 수 있어서, 다시 요청하면 그때 비활성으로 처리된다) */
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CATEGORY_PATHS, allEntries = true)
    public void delete(Long categoryId) {
        Category category = getCategoryOrThrow(categoryId);
        boolean inUse = courseRepository.existsByCategoryId(categoryId)
                || categoryRepository.existsByParentId(categoryId);
        if (inUse) {
            category.deactivate();
            return;
        }
        try {
            categoryRepository.delete(category);
            categoryRepository.flush();   // DELETE를 바로 실행해서 FK 위반을 이 메서드 안에서 잡는다
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(CourseErrorCode.CATEGORY_IN_USE);
        }
    }

    private Category getCategoryOrThrow(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}