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
 *   테이블 scrap (강의 스크랩, LRN-08, SP-39)
 *   created_at(스크랩한 시각)은 CreatedTimeEntity(JPA 감사, 서울 시간)가 채운다 (#157)
 *  */
@Entity
@Table(name = "scrap")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Scrap extends CreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long scrapId;

    private Long memberId;
    private Long courseId;

    private Scrap(Long memberId, Long courseId) {
        this.memberId = memberId;
        this.courseId = courseId;
    }

    public static Scrap create(Long memberId, Long courseId) {
        return new Scrap(memberId, courseId);
    }
}
