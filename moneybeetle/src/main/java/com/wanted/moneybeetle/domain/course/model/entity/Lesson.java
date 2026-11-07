package com.wanted.moneybeetle.domain.course.model.entity;

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
 *   테이블 lesson (영상 + 요약글, 강의당 1~3개)
 *  */
@Entity
@Table(name = "lesson")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long lessonId;

    private Long courseId;          // 같은 도메인이지만 우선 ID로 참조 (연관관계는 팀 합의 전)
    private int sortOrder;          // 영상 순서 1~3
    private String title;
    private String videoUrl;
    private int durationSeconds;    // 재생 시간(초)
    private String summary;         // 요약글 (2,000자 이내)
}