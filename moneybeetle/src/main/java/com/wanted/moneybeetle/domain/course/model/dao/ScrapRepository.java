package com.wanted.moneybeetle.domain.course.model.dao;

import com.wanted.moneybeetle.domain.course.model.entity.Scrap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   테이블 scrap
 *  */
public interface ScrapRepository extends JpaRepository<Scrap, Long> {

    boolean existsByMemberIdAndCourseId(Long memberId, Long courseId);

    long deleteByMemberIdAndCourseId(Long memberId, Long courseId);

    Page<Scrap> findByMemberIdOrderByCreatedAtDesc(Long memberId, Pageable pageable);

    /* 강의 목록 한 페이지에서 내가 스크랩한 강의를 한 번에 찾는다 */
    List<Scrap> findByMemberIdAndCourseIdIn(Long memberId, List<Long> courseIds);
}