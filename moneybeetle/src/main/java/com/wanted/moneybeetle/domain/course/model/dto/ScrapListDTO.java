package com.wanted.moneybeetle.domain.course.model.dto;

import com.wanted.moneybeetle.domain.course.model.entity.Course;
import com.wanted.moneybeetle.domain.course.model.entity.Scrap;
import lombok.Getter;

import java.time.LocalDateTime;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   API 38. 스크랩 목록 항목
 *  */
@Getter
public class ScrapListDTO {

    private final Long courseId;
    private final String title;
    private final String categoryName;   // 예: "부동산 › 청약"
    private final LocalDateTime scrappedAt;

    private ScrapListDTO(Long courseId, String title, String categoryName, LocalDateTime scrappedAt) {
        this.courseId = courseId;
        this.title = title;
        this.categoryName = categoryName;
        this.scrappedAt = scrappedAt;
    }

    public static ScrapListDTO from(Scrap scrap, Course course, String categoryName) {
        return new ScrapListDTO(course.getCourseId(), course.getTitle(), categoryName, scrap.getCreatedAt());
    }
}