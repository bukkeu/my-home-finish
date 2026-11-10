package com.wanted.moneybeetle.domain.course.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wanted.moneybeetle.domain.course.model.entity.Course;
import java.time.LocalDate;
import lombok.Getter;

/* comment.
*   담당 : DBA ① (강의·퀴즈)
*   강의 목록 한 줄 (API 30, 홈 '이어서 볼 강의')
*   courseId, categoryId(소주제), title, instructorName, thumbnailUrl, lessonCount, watchedLessonCount, status, infoBaseDate, isScrapped
*   - 화면(#52)이 소주제별로 묶고 진행률(watchedLessonCount / lessonCount)을 그린다
*  */
@Getter
public class CourseListDTO {

    private Long courseId;
    private Long categoryId;          // 소주제 ID (화면이 소주제별로 묶는다)
    private String title;
    private String instructorName;    // 강사 이름 (강사 정보 표시 이름, 없으면 null)
    private String thumbnailUrl;
    private int lessonCount;          // 영상 수
    private int watchedLessonCount;   // 내가 끝까지 본 영상 수 (비로그인·안 들음이면 0)
    private String status;            // 내 학습 상태: IN_PROGRESS / COMPLETED / null(안 들음)
    private LocalDate infoBaseDate;   // 제도 정보 기준일 (NFR-15, 없으면 null)
    @JsonProperty("isScrapped")       // JSON에서 "isScrapped"로 나가게 (없으면 "scrapped"로 바뀜)
    private boolean scrapped;

    private CourseListDTO() {
    }

    // Entity → DTO 변환은 DTO 쪽에 둔다 (코딩 컨벤션 5장)
    public static CourseListDTO from(Course course, String instructorName, int lessonCount, int watchedLessonCount,
                                     String status, boolean scrapped) {
        CourseListDTO dto = new CourseListDTO();
        dto.courseId = course.getCourseId();
        dto.categoryId = course.getCategoryId();
        dto.title = course.getTitle();
        dto.instructorName = instructorName;
        dto.thumbnailUrl = course.getThumbnailUrl();
        dto.lessonCount = lessonCount;
        dto.watchedLessonCount = watchedLessonCount;
        dto.status = status;
        dto.infoBaseDate = course.getInfoBaseDate();
        dto.scrapped = scrapped;
        return dto;
    }
}
