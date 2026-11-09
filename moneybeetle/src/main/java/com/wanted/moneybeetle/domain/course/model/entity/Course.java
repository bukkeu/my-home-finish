package com.wanted.moneybeetle.domain.course.model.entity;

import com.wanted.moneybeetle.domain.course.exception.CourseErrorCode;
import com.wanted.moneybeetle.global.exception.BusinessException;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   테이블 course (DRAFT -> REVIEW -> APPROVED/REJECTED, is_public, Soft Delete)
 *  */
@Entity
@Table(name = "course")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course {

    // 상태값은 Entity가 상수로 가진다 (컨벤션 4장)
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_REVIEW = "REVIEW";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long courseId;

    private Long instructorId;      // 다른 도메인(member)은 ID로만 참조
    private Long categoryId;
    private String title;
    private String description;
    private String thumbnailUrl;
    private String status;
    private boolean isPublic;
    private LocalDate infoBaseDate;
    private String infoSource;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime deletedAt;

    // 사용자에게 보여도 되는 강의인지는 Entity가 판단 (SP-31)
    public boolean isVisibleToLearner() {
        return STATUS_APPROVED.equals(status) && isPublic && deletedAt == null;
    }

    public boolean isInReview() {
        return STATUS_REVIEW.equals(status);
    }

    public boolean isApproved() {
        return STATUS_APPROVED.equals(status);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /* 허가 = 허가 + 공개 (API 91). 검수 중인 강의만 */
    public void approve(LocalDateTime approvedAt) {
        requireInReview();
        this.status = STATUS_APPROVED;
        this.isPublic = true;
        this.approvedAt = approvedAt;
    }

    /* 반려 (API 92). 사유는 course_review에 남긴다. 반려된 강의는 보이지 않게 비공개 */
    public void reject() {
        requireInReview();
        this.status = STATUS_REJECTED;
        this.isPublic = false;
    }

    /* 공개·비공개 (API 93). 허가된 강의만 바꿀 수 있다 → 허가 안 된 강의가 공개되는 일을 막는다 */
    public void changeVisibility(boolean isPublic) {
        if (!isApproved()) {
            throw new BusinessException(CourseErrorCode.COURSE_NOT_APPROVED);
        }
        this.isPublic = isPublic;
    }

    /* Soft Delete (API 94). 학습 기록 확인은 서비스가 한다 */
    public void delete(LocalDateTime deletedAt) {
        this.isPublic = false;
        this.deletedAt = deletedAt;
    }

    /* 이 강의의 강사인지 (본인 강의만 수정·검수 요청, #30·#32) */
    public boolean isOwnedBy(Long instructorId) {
        return Objects.equals(this.instructorId, instructorId);
    }

    /* 검수 요청 (API 81, DP-34). 작성 중·반려 상태에서만 → 반려된 강의를 고쳐 다시 요청할 수 있다 */
    public void requestReview(LocalDateTime submittedAt) {
        if (!STATUS_DRAFT.equals(status) && !STATUS_REJECTED.equals(status)) {
            throw new BusinessException(CourseErrorCode.COURSE_NOT_REQUESTABLE);
        }
        this.status = STATUS_REVIEW;
        this.submittedAt = submittedAt;
    }

    private void requireInReview() {
        if (!isInReview()) {
            throw new BusinessException(CourseErrorCode.COURSE_NOT_IN_REVIEW);
        }
    }
}
