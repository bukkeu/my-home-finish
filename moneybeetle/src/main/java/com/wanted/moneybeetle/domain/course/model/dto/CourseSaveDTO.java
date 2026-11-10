package com.wanted.moneybeetle.domain.course.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   API 73 강의 생성·API 75 강의 수정 요청 (TCH-01·06, NFR-15)
 *   { "categoryId": 21, "title": "...", "description": "...", "infoBaseDate": "2026-10-01", "infoSource": "청약홈" }
 *  */
public record CourseSaveDTO(
        @NotNull(message = "소주제를 골라 주세요") Long categoryId,
        @NotBlank(message = "강의 제목을 입력해 주세요")
        @Size(max = 100, message = "강의 제목은 100자까지 쓸 수 있어요") String title,
        @Size(max = 2000, message = "강의 소개는 2,000자까지 쓸 수 있어요") String description,
        LocalDate infoBaseDate,
        @Size(max = 200, message = "정보 출처는 200자까지 쓸 수 있어요") String infoSource
) {
}
