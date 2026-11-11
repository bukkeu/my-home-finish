package com.wanted.moneybeetle.domain.course.model.entity;

import com.wanted.moneybeetle.domain.course.exception.CourseErrorCode;
import com.wanted.moneybeetle.global.common.BaseTimeEntity;
import com.wanted.moneybeetle.global.exception.BusinessException;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   테이블 course (DRAFT -> REVIEW -> APPROVED/REJECTED, is_public, Soft Delete)
 *   created_at·updated_at 은 BaseTimeEntity(JPA 감사, 서울 시간)가 채운다 (#157)
 *  */
@Entity
@Table(name = "course")
@DynamicUpdate      // 바뀐 컬럼만 UPDATE: 검수 요청·수정과 관리자 삭제가 동시에 저장돼도 서로의 값을 옛 값으로 덮어쓰지 않는다 (#32 리뷰)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course extends BaseTimeEntity {

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

    private Course(Long instructorId, Long categoryId, String title, String description,
                   LocalDate infoBaseDate, String infoSource) {
        this.instructorId = instructorId;
        this.categoryId = categoryId;
        this.title = title;
        this.description = description;
        this.infoBaseDate = infoBaseDate;
        this.infoSource = infoSource;
        this.status = STATUS_DRAFT;
        this.isPublic = false;
    }

    /* 강의 생성 (API 73, TCH-01). 작성 중(DRAFT)·비공개로 시작한다 */
    public static Course create(Long instructorId, Long categoryId, String title, String description,
                                LocalDate infoBaseDate, String infoSource) {
        return new Course(instructorId, categoryId, title, description, infoBaseDate, infoSource);
    }

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

    /* 검수 요청을 할 수 있는 상태인지 (API 81). 작성 중·반려 상태만 → 반려된 강의를 고쳐 다시 요청할 수 있다
     * 서비스가 콘텐츠(영상·퀴즈) 검사보다 먼저 불러 상태 오류(409)가 콘텐츠 오류보다 앞서게 한다 */
    public void checkRequestable() {
        if (!STATUS_DRAFT.equals(status) && !STATUS_REJECTED.equals(status)) {
            throw new BusinessException(CourseErrorCode.COURSE_NOT_REQUESTABLE);
        }
    }

    /* 검수 요청 (API 81, DP-34). 상태 변경 자체도 같은 검사로 보호한다 */
    public void requestReview(LocalDateTime submittedAt) {
        checkRequestable();
        this.status = STATUS_REVIEW;
        this.submittedAt = submittedAt;
    }

    /* 강의 정보 수정 (API 75, TCH-06·SP-31). 검수 중에는 못 고친다.
     * 허가된 강의를 고치면 다시 검수(REVIEW) → 허가 전까지 사용자에게 보이지 않는다(isVisibleToLearner) */
    public void change(Long categoryId, String title, String description, LocalDate infoBaseDate, String infoSource,
                       LocalDateTime now) {
        checkEditable();
        this.categoryId = categoryId;
        this.title = title;
        this.description = description;
        this.infoBaseDate = infoBaseDate;
        this.infoSource = infoSource;
        reReviewIfApproved(now);
    }

    /* 썸네일 주소 바꾸기 (API 77). 검수 중에는 못 바꾼다.
     * 허가된 강의의 썸네일을 바꾸면 정보 수정과 같이 다시 검수(REVIEW, SP-31) */
    public void changeThumbnail(String thumbnailUrl, LocalDateTime now) {
        checkEditable();
        this.thumbnailUrl = thumbnailUrl;
        reReviewIfApproved(now);
    }

    /* 영상·요약글·테스트 퀴즈·퀴즈를 바꿨을 때 (API 78~80, #31). 검수 중에는 못 바꾼다.
     * 허가된 강의의 내용을 바꾸면 정보 수정과 같이 다시 검수(REVIEW, SP-31) */
    public void markContentChanged(LocalDateTime now) {
        checkEditable();
        reReviewIfApproved(now);
    }

    /* 허가된 강의를 고치면 다시 검수 → 허가 전까지 사용자에게 보이지 않는다 (SP-31) */
    private void reReviewIfApproved(LocalDateTime now) {
        if (isApproved()) {
            this.status = STATUS_REVIEW;
            this.submittedAt = now;
        }
    }

    /* 수정할 수 있는 상태인지 (API 75·77). 검수 중이면 COURSE_IN_REVIEW
     * 서비스가 파일 저장처럼 되돌리기 어려운 작업 전에 먼저 부른다 */
    public void checkEditable() {
        if (isInReview()) {
            throw new BusinessException(CourseErrorCode.COURSE_IN_REVIEW);
        }
    }

    private void requireInReview() {
        if (!isInReview()) {
            throw new BusinessException(CourseErrorCode.COURSE_NOT_IN_REVIEW);
        }
    }
}
