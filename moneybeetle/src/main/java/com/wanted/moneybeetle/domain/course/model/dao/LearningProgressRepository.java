package com.wanted.moneybeetle.domain.course.model.dao;

import com.wanted.moneybeetle.domain.course.model.entity.LearningProgress;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   테이블 learning_progress
 *  */
public interface LearningProgressRepository extends JpaRepository<LearningProgress, Long> {

    /* 이 회원이 이 강의를 본 기록 (이어보기 위치) */
    Optional<LearningProgress> findByMemberIdAndCourseId(Long memberId, Long courseId);

    /* 학습 완료 목록 (최근 완료 순) */
    Page<LearningProgress> findByMemberIdAndStatusOrderByCompletedAtDesc(Long memberId, String status, Pageable pageable);

    /* 홈 '이어서 볼 강의' (최근에 본 순서 5개) */
    List<LearningProgress> findTop5ByMemberIdAndStatusOrderByUpdatedAtDesc(Long memberId, String status);

    /* 학습 기록이 하나라도 있는지 (홈 vr1/vr2 분기) */
    boolean existsByMemberId(Long memberId);
}