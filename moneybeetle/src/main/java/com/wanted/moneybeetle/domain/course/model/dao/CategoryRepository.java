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
}
