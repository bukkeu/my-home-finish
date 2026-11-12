package com.wanted.moneybeetle.domain.course.model.dto;

import com.wanted.moneybeetle.domain.course.model.entity.Course;
import java.time.LocalDateTime;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   API 72 내 강의 목록 한 줄 (검수 상태·공개 여부·수강생 수·답변 대기 문의 수·최근 반려 사유)
 *   #369 : 카테고리(categoryName 소주제, parentCategoryName 상위 카테고리)·난이도·구성(영상 수·총 재생 시간 초)·등록일 추가
 *  */
public record MyCourseItemDTO(
        Long courseId,
        String title,
        String status,
        boolean isPublic,
        String thumbnailUrl,
        long learners,
        long waitingInquiries,
        String rejectReason,
        Long categoryId,
        String categoryName,
        String parentCategoryName,
        String level,
        long lessonCount,
        long totalDurationSeconds,
        LocalDateTime createdAt
) {
    /** 강의 한 줄에 붙일 값들 (Service 가 강의 여러 개를 한 번에 모아 온다) */
    public record Extra(long learners, long waitingInquiries, String rejectReason, String categoryName,
                        String parentCategoryName, long lessonCount, long totalDurationSeconds) {
    }

    public static MyCourseItemDTO of(Course course, Extra extra) {
        return new MyCourseItemDTO(course.getCourseId(), course.getTitle(), course.getStatus(), course.isPublic(),
                course.getThumbnailUrl(), extra.learners(), extra.waitingInquiries(), extra.rejectReason(),
                course.getCategoryId(), extra.categoryName(), extra.parentCategoryName(), course.getLevel().name(),
                extra.lessonCount(), extra.totalDurationSeconds(), course.getCreatedAt());
    }
}
