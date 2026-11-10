package com.wanted.moneybeetle.domain.course.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wanted.moneybeetle.domain.course.model.entity.Category;
import com.wanted.moneybeetle.domain.course.model.entity.Course;
import com.wanted.moneybeetle.domain.course.model.entity.Lesson;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/* comment.
*   담당 : DBA ① (강의·퀴즈)
*   강의 상세 (영상·요약글, 진행 상태, 스크랩)
*   - progressStatus: 내 학습 상태 IN_PROGRESS / COMPLETED / null, lastLessonId: 이어보기할 영상 (#52)
*   - lessons[].isCompleted: 내가 끝까지 본 영상인지 (서버가 정한 시청 완료, #20)
*  */
@Getter
public class CourseDetailDTO {

    private Long courseId;
    private String title;
    private String description;
    private Long categoryId;
    private String categoryName;
    private Long instructorId;
    private String instructorName;   // 강사 이름 (강사 정보 표시 이름, 없으면 null)
    private LocalDate infoBaseDate;  // 제도 정보 기준일 (NFR-15)
    private String infoSource;       // 제도 정보 출처
    private List<LessonItem> lessons;

    @JsonProperty("isScrapped")
    private boolean scrapped;
    private String progressStatus;   // 내 학습 상태 (비로그인·안 들음이면 null)
    private Long lastLessonId;       // 마지막으로 본 영상 (이어보기, 없으면 null)

    private CourseDetailDTO() {
    }

    public static CourseDetailDTO from(Course course, Category category, String instructorName, List<Lesson> lessons,
                                       Set<Long> completedLessonIds, boolean scrapped,
                                       String progressStatus, Long lastLessonId) {
        CourseDetailDTO dto = new CourseDetailDTO();
        dto.courseId = course.getCourseId();
        dto.title = course.getTitle();
        dto.description = course.getDescription();
        dto.categoryId = course.getCategoryId();
        dto.categoryName = (category != null) ? category.getName() : null;
        dto.instructorId = course.getInstructorId();
        dto.instructorName = instructorName;
        dto.infoBaseDate = course.getInfoBaseDate();
        dto.infoSource = course.getInfoSource();
        dto.lessons = lessons.stream()
                .map(lesson -> LessonItem.from(lesson, completedLessonIds.contains(lesson.getLessonId())))
                .toList();
        dto.scrapped = scrapped;
        dto.progressStatus = progressStatus;
        dto.lastLessonId = lastLessonId;
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
        @JsonProperty("isCompleted")
        private boolean completed;      // 끝까지 본 영상인지 (JSON "isCompleted", API 명세서 31)

        private LessonItem() {
        }

        public static LessonItem from(Lesson lesson, boolean completed) {
            LessonItem item = new LessonItem();
            item.lessonId = lesson.getLessonId();
            item.sortOrder = lesson.getSortOrder();
            item.title = lesson.getTitle();
            item.durationSeconds = lesson.getDurationSeconds();
            item.summary = lesson.getSummary();
            item.completed = completed;
            return item;
        }
    }
}
