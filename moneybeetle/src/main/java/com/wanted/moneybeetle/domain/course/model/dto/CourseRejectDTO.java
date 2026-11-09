package com.wanted.moneybeetle.domain.course.model.dto;

import com.wanted.moneybeetle.domain.course.model.entity.CourseReview;
import jakarta.validation.constraints.Size;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   API 92 반려. 빈 사유는 서비스에서 REASON_REQUIRED로 거절한다 (명세 에러 코드)
 *  */
public record CourseRejectDTO(
        @Size(max = CourseReview.REASON_MAX_LENGTH, message = "반려 사유는 500자까지 쓸 수 있어요") String reason
) {
}