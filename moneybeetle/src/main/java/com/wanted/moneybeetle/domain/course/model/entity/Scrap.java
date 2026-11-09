package com.wanted.moneybeetle.domain.course.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   테이블 scrap (강의 스크랩, LRN-08, SP-39)
 *  */
@Entity
@Table(name = "scrap")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Scrap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long scrapId;

    private Long memberId;
    private Long courseId;
    private LocalDateTime createdAt;

    private Scrap(Long memberId, Long courseId) {
        this.memberId = memberId;
        this.courseId = courseId;
        this.createdAt = LocalDateTime.now();
    }

    public static Scrap create(Long memberId, Long courseId) {
        return new Scrap(memberId, courseId);
    }
}
