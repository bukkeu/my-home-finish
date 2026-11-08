package com.wanted.moneybeetle.domain.course.model.entity;

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
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long categoryId;

    private Long parentId;      // 상위 카테고리. null이면 최상위 (돈 굴리기, 부동산)
    private String name;
    private int depth;          // 1 카테고리 / 2 소주제
    private int sortOrder;      // 정렬 순서
    private boolean isActive;   // 사용 여부 (false면 숨김, Soft Delete)

    // 상위 카테고리가 있으면 소주제 (컨벤션 4장: 상태 해석은 Entity가)
    public boolean isSubCategory() {
        return parentId != null;
    }
}
