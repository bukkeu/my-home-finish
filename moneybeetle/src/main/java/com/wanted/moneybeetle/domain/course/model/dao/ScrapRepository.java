package com.wanted.moneybeetle.domain.course.model.dao;

import com.wanted.moneybeetle.domain.course.model.entity.Scrap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   테이블 scrap
 *  */
public interface ScrapRepository extends JpaRepository<Scrap, Long> {

    boolean existsByMemberIdAndCourseId(Long memberId, Long courseId);

    long deleteByMemberIdAndCourseId(Long memberId, Long courseId);

    /* 내 스크랩 목록 (API 38): 지금 사용자에게 보이는 강의(허가 + 공개 + 삭제 안 됨, isVisibleToLearner)만.
     * 재검수(REVIEW)·비공개·삭제된 강의는 숨기고 스크랩 기록은 남긴다 → 다시 허가되면 목록에 돌아온다 (SP-31).
     * 최근 스크랩 순. 같은 시각(초 단위)이면 scrapId로 순서를 고정해 페이지 경계에서 중복·누락을 막는다 */
    @Query(value = """
            select s from Scrap s, Course c
            where c.courseId = s.courseId
              and s.memberId = :memberId
              and c.status = com.wanted.moneybeetle.domain.course.model.entity.CourseStatus.APPROVED and c.isPublic = true and c.deletedAt is null
            order by s.createdAt desc, s.scrapId desc
            """,
            countQuery = """
            select count(s) from Scrap s, Course c
            where c.courseId = s.courseId
              and s.memberId = :memberId
              and c.status = com.wanted.moneybeetle.domain.course.model.entity.CourseStatus.APPROVED and c.isPublic = true and c.deletedAt is null
            """)
    Page<Scrap> findVisibleScraps(@Param("memberId") Long memberId, Pageable pageable);

    /* 강의 목록 한 페이지에서 내가 스크랩한 강의를 한 번에 찾는다 */
    List<Scrap> findByMemberIdAndCourseIdIn(Long memberId, List<Long> courseIds);
}