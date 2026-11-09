package com.wanted.moneybeetle.domain.course.model.dto;

import jakarta.validation.constraints.NotNull;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   API 93 공개·비공개
 *  */
public record CourseVisibilityDTO(
        @NotNull(message = "공개 여부를 골라 주세요") Boolean isPublic
) {
}