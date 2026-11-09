package com.wanted.moneybeetle.domain.course.model.dto;

import lombok.Getter;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   다른 도메인(퀴즈 오답노트)에 넘기는 강의 이름표: 강의 ID·제목·카테고리 경로
 *  */
@Getter
public class CourseLabelDTO {

    private final Long courseId;
    private final String title;
    private final String categoryName;   // 예: "부동산 › 청약"

    private CourseLabelDTO(Long courseId, String title, String categoryName) {
        this.courseId = courseId;
        this.title = title;
        this.categoryName = categoryName;
    }

    public static CourseLabelDTO of(Long courseId, String title, String categoryName) {
        return new CourseLabelDTO(courseId, title, categoryName);
    }
}
