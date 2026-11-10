package com.wanted.moneybeetle.domain.course.model.dto;

import com.wanted.moneybeetle.domain.course.model.entity.Course;
import com.wanted.moneybeetle.domain.course.model.entity.Lesson;
import com.wanted.moneybeetle.domain.quiz.model.dto.QuizReviewDTO;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   API 74 강의 편집 정보 (TCH-01~05): 강의 정보 + 영상·요약글 + 테스트 퀴즈·퀴즈(정답·해설 포함) + 최근 반려 사유
 *   본인 강의에만 내려준다 (정답 포함)
 *  */
public record CourseEditDTO(
        Long courseId,
        Long categoryId,
        String title,
        String description,
        String thumbnailUrl,
        String status,
        boolean isPublic,
        LocalDate infoBaseDate,
        String infoSource,
        LocalDateTime submittedAt,
        LocalDateTime approvedAt,
        String rejectReason,
        List<LessonItem> lessons,
        List<QuizReviewDTO> quizzes
) {
    public record LessonItem(Long lessonId, int sortOrder, String title, String videoUrl,
                             int durationSeconds, String summary) {
        static LessonItem from(Lesson lesson) {
            return new LessonItem(lesson.getLessonId(), lesson.getSortOrder(), lesson.getTitle(),
                    lesson.getVideoUrl(), lesson.getDurationSeconds(), lesson.getSummary());
        }
    }

    public static CourseEditDTO of(Course course, String rejectReason, List<Lesson> lessons,
                                   List<QuizReviewDTO> quizzes) {
        return new CourseEditDTO(course.getCourseId(), course.getCategoryId(), course.getTitle(),
                course.getDescription(), course.getThumbnailUrl(), course.getStatus(), course.isPublic(),
                course.getInfoBaseDate(), course.getInfoSource(), course.getSubmittedAt(), course.getApprovedAt(),
                rejectReason, lessons.stream().map(LessonItem::from).toList(), quizzes);
    }
}
