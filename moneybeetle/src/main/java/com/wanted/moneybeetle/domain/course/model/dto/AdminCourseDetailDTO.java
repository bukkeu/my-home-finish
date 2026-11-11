package com.wanted.moneybeetle.domain.course.model.dto;

import com.wanted.moneybeetle.domain.course.model.entity.Course;
import com.wanted.moneybeetle.domain.course.model.entity.CourseReview;
import com.wanted.moneybeetle.domain.course.model.entity.Lesson;
import com.wanted.moneybeetle.domain.quiz.model.dto.QuizReviewDTO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   API 90 검수 상세: 강의 편집 정보 + 영상·요약글 + 퀴즈(정답 포함) + 검수 이력
 *  */
public record AdminCourseDetailDTO(
        Long courseId,
        String title,
        String description,
        String thumbnailUrl,
        Long categoryId,
        Long instructorId,
        String instructorName,
        String status,
        boolean isPublic,
        LocalDate infoBaseDate,
        String infoSource,
        LocalDateTime submittedAt,
        LocalDateTime approvedAt,
        List<LessonItem> lessons,
        List<QuizReviewDTO> quizzes,
        List<ReviewItem> reviews
) {
    public record LessonItem(Long lessonId, int sortOrder, String title, String videoUrl,
                             int durationSeconds, String summary) {
        static LessonItem from(Lesson lesson) {
            return new LessonItem(lesson.getLessonId(), lesson.getSortOrder(), lesson.getTitle(),
                    lesson.getVideoUrl(), lesson.getDurationSeconds(), lesson.getSummary());
        }
    }

    public record ReviewItem(Long courseReviewId, Long adminId, String result, String reason, LocalDateTime createdAt) {
        static ReviewItem from(CourseReview review) {
            return new ReviewItem(review.getCourseReviewId(), review.getAdminId(), review.getResult(),
                    review.getReason(), review.getCreatedAt());
        }
    }

    public static AdminCourseDetailDTO of(Course course, String instructorName, List<Lesson> lessons,
                                          List<QuizReviewDTO> quizzes, List<CourseReview> reviews) {
        return new AdminCourseDetailDTO(course.getCourseId(), course.getTitle(), course.getDescription(),
                course.getThumbnailUrl(), course.getCategoryId(), course.getInstructorId(), instructorName,
                course.getStatus(), course.isPublic(), course.getInfoBaseDate(), course.getInfoSource(),
                course.getSubmittedAt(), course.getApprovedAt(),
                lessons.stream().map(LessonItem::from).toList(),
                quizzes,
                reviews.stream().map(ReviewItem::from).toList());
    }
}