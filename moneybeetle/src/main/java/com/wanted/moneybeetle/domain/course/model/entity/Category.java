package com.wanted.moneybeetle.domain.course.model.entity;

import com.wanted.moneybeetle.domain.course.exception.CourseErrorCode;
import com.wanted.moneybeetle.global.common.BaseTimeEntity;
import com.wanted.moneybeetle.global.exception.BusinessException;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   테이블 category (자기 참조 2단계 : 카테고리 -> 소주제)
 *  */
@Entity
@Table(name = "category")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseTimeEntity {

    public static final int DEPTH_CATEGORY = 1;
    public static final int DEPTH_SUB_CATEGORY = 2;
    public static final int NAME_MAX_LENGTH = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long categoryId;

    private Long parentId;      // 상위 카테고리. null이면 최상위 (돈 굴리기, 부동산)
    private String name;
    private int depth;          // 1 카테고리 / 2 소주제
    private int sortOrder;      // 정렬 순서
    private boolean isActive;   // 사용 여부 (false면 숨김, Soft Delete)

    private Category(Long parentId, String name, int depth, int sortOrder) {
        this.parentId = parentId;
        this.name = name;
        this.depth = depth;
        this.sortOrder = sortOrder;
        this.isActive = true;
    }

    /* 최상위 카테고리 (API 86, parentId 없음) */
    public static Category createCategory(String name, int sortOrder) {
        return new Category(null, name, DEPTH_CATEGORY, sortOrder);
    }

    /* 소주제 (API 86). 2단계까지만: 소주제 아래에는 만들 수 없다 */
    public static Category createSubCategory(Category parent, String name, int sortOrder) {
        if (parent.isSubCategory()) {
            throw new BusinessException(CourseErrorCode.CATEGORY_DEPTH_EXCEEDED);
        }
        return new Category(parent.getCategoryId(), name, DEPTH_SUB_CATEGORY, sortOrder);
    }

    // 상위 카테고리가 있으면 소주제 (컨벤션 4장: 상태 해석은 Entity가)
    public boolean isSubCategory() {
        return parentId != null;
    }

    /* 이름·순서·사용 여부 수정 (API 87) */
    public void change(String name, int sortOrder, boolean isActive) {
        this.name = name;
        this.sortOrder = sortOrder;
        this.isActive = isActive;
    }

    /* 강의·소주제가 연결돼 있어 지울 수 없을 때 숨긴다 (API 88, Soft Delete) */
    public void deactivate() {
        this.isActive = false;
    }
}