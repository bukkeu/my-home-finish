package com.wanted.moneybeetle.domain.course.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
/* comment.
*   담당 : DBA ① (강의·퀴즈)
*   시청 위치 저장 (positionSeconds, watchedSeconds, completed)
*  */
@Getter
@NoArgsConstructor
public class ProgressDTO {

    @NotNull(message = "재생 위치를 보내 주세요")
    @Min(value = 0, message = "재생 위치는 0초 이상이어야 해요")
    private Integer positionSeconds;

    @NotNull(message = "본 시간을 보내 주세요")
    @Min(value = 0, message = "본 시간은 0초 이상이어야 해요")
    private Integer watchedSeconds;

    private boolean completed;
}