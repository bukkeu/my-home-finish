package com.wanted.moneybeetle.domain.course.model.entity;

import com.wanted.moneybeetle.global.common.CreatedTimeEntity;
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
 *   테이블 course_review. 허가·반려 이력 (지우지 않고 쌓는다, DP-43)
 *  */
@Entity
@Table(name = "course_review")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseReview extends CreatedTimeEntity {

    public static final String RESULT_APPROVED = "APPROVED";
    public static final String RESULT_REJECTED = "REJECTED";
    public static final int REASON_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long courseReviewId;

    private Long courseId;
    private Long adminId;
    private String result;
    private String reason;      // 반려일 때만

    private CourseReview(Long courseId, Long adminId, String result, String reason) {
        this.courseId = courseId;
        this.adminId = adminId;
        this.result = result;
        this.reason = reason;
    }

    public static CourseReview approved(Long courseId, Long adminId) {
        return new CourseReview(courseId, adminId, RESULT_APPROVED, null);
    }

    public static CourseReview rejected(Long courseId, Long adminId, String reason) {
        return new CourseReview(courseId, adminId, RESULT_REJECTED, reason);
    }
}