package com.wanted.moneybeetle.domain.course.model.entity;

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
}