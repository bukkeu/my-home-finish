package com.wanted.moneybeetle.domain.course.model.dto;

import com.wanted.moneybeetle.domain.course.model.entity.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   API 87 수정 { "name": "전·월세", "sortOrder": 1, "isActive": true }
 *  */
public record CategoryUpdateDTO(
        @NotBlank(message = "이름을 입력해 주세요")
        @Size(max = Category.NAME_MAX_LENGTH, message = "이름은 30자까지 쓸 수 있어요") String name,
        @NotNull(message = "순서를 입력해 주세요")
        @PositiveOrZero(message = "순서는 0 이상이어야 해요") Integer sortOrder,
        @NotNull(message = "사용 여부를 골라 주세요") Boolean isActive
) {
}