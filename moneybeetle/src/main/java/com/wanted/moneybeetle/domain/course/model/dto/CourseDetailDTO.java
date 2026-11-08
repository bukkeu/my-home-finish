package com.wanted.moneybeetle.domain.course.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wanted.moneybeetle.domain.course.model.entity.Category;
import com.wanted.moneybeetle.domain.course.model.entity.Course;
import com.wanted.moneybeetle.domain.course.model.entity.Lesson;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

/* comment.
*   담당 : DBA ① (강의·퀴즈)
*   강의 상세 (영상·요약글, 진행 상태, 퀴즈 열림 여부, 스크랩)
*  */
@Getter
public class CourseDetailDTO {

    private Long courseId;
    private String title;
    private String description;
    private Long categoryId;
    private String categoryName;
    private Long instructorId;
    private String instructorName;   // TODO DBA ② 메서드가 생기면 연결
    private LocalDate infoBaseDate;  // 제도 정보 기준일 (NFR-15)
    private String infoSource;       // 제도 정보 출처
    private List<LessonItem> lessons;

    @JsonProperty("isScrapped")
    private boolean scrapped;        // TODO 스크랩 이슈에서 연결

    private CourseDetailDTO() {
    }

    public static CourseDetailDTO from(Course course, Category category, List<Lesson> lessons, boolean scrapped) {
        CourseDetailDTO dto = new CourseDetailDTO();
        dto.courseId = course.getCourseId();
        dto.title = course.getTitle();
        dto.description = course.getDescription();
        dto.categoryId = course.getCategoryId();
        dto.categoryName = (category != null) ? category.getName() : null;
        dto.instructorId = course.getInstructorId();
        dto.infoBaseDate = course.getInfoBaseDate();
        dto.infoSource = course.getInfoSource();
        dto.lessons = lessons.stream().map(LessonItem::from).toList();
        dto.scrapped = scrapped;
        return dto;
    }

    /* 강의 상세에 보여 줄 영상 한 줄. 영상 주소는 재생 API(32)에서 따로 준다 */
    @Getter
    public static class LessonItem {
        private Long lessonId;
        private int sortOrder;
        private String title;
        private int durationSeconds;
        private String summary;

        private LessonItem() {
        }

        public static LessonItem from(Lesson lesson) {
            LessonItem item = new LessonItem();
            item.lessonId = lesson.getLessonId();
            item.sortOrder = lesson.getSortOrder();
            item.title = lesson.getTitle();
            item.durationSeconds = lesson.getDurationSeconds();
            item.summary = lesson.getSummary();
            return item;
        }
    }
}
