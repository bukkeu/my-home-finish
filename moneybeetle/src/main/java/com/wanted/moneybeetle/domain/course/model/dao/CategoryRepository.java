package com.wanted.moneybeetle.domain.course.model.dao;

import com.wanted.moneybeetle.domain.course.model.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   테이블 category
 *  */
public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByIsActiveTrueOrderByDepthAscSortOrderAsc();

    List<Category> findByParentIdAndIsActiveTrue(Long parentId);

    /* 관리자 트리 (API 85): 비활성 포함, 단계 → 순서 → ID로 정렬 고정 */
    List<Category> findAllByOrderByDepthAscSortOrderAscCategoryIdAsc();

    /* 삭제 판단 (API 88): 하위 소주제가 있으면 실제 삭제 불가 (FK) */
    boolean existsByParentId(Long parentId);
}