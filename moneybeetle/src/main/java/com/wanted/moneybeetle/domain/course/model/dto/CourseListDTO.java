package com.wanted.moneybeetle.domain.course.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wanted.moneybeetle.domain.course.model.entity.Course;
import lombok.Getter;

/* comment.
*   담당 : DBA ① (강의·퀴즈)
*   강의 목록 한 줄 (courseId, title, instructorName, lessonCount, status, isScrapped)
*  */
@Getter
public class CourseListDTO {


    private Long courseId;
    private String title;
    private String instructorName;   // 강사 이름 (member 도메인에서 받아옴, 지금은 null 가능)
    private String thumbnailUrl;
    private int lessonCount;         // 영상 수
    private String status;           // 내 학습 상태: IN_PROGRESS / COMPLETED / null(안 들음)

    @JsonProperty("isScrapped")      // JSON에서 "isScrapped"로 나가게 (없으면 "scrapped"로 바뀜)
    private boolean scrapped;

    private CourseListDTO() {
    }

    // Entity → DTO 변환은 DTO 쪽에 둔다 (코딩 컨벤션 5장)
    public static CourseListDTO from(Course course, String instructorName, int lessonCount,
                                     String status, boolean scrapped) {
        CourseListDTO dto = new CourseListDTO();
        dto.courseId = course.getCourseId();
        dto.title = course.getTitle();
        dto.instructorName = instructorName;
        dto.thumbnailUrl = course.getThumbnailUrl();
        dto.lessonCount = lessonCount;
        dto.status = status;
        dto.scrapped = scrapped;
        return dto;
    }
}
