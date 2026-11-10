package com.wanted.moneybeetle.domain.course.model.dto;

import com.wanted.moneybeetle.domain.course.model.entity.Course;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   API 72 내 강의 목록 한 줄 (검수 상태·공개 여부·수강생 수·답변 대기 문의 수·최근 반려 사유)
 *  */
public record MyCourseItemDTO(
        Long courseId,
        String title,
        String status,
        boolean isPublic,
        String thumbnailUrl,
        long learners,
        long waitingInquiries,
        String rejectReason
) {
    public static MyCourseItemDTO of(Course course, long learners, long waitingInquiries, String rejectReason) {
        return new MyCourseItemDTO(course.getCourseId(), course.getTitle(), course.getStatus(), course.isPublic(),
                course.getThumbnailUrl(), learners, waitingInquiries, rejectReason);
    }
}
