package com.wanted.moneybeetle.domain.course.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/* comment.
*   담당 : DBA ① (강의·퀴즈)
*   API 78 영상·요약글 일괄 저장 요청 (최대 3개, 요약 2,000자)
*   { "lessons": [ { "lessonId": 20, "sortOrder": 1, "title": "...", "videoUrl": "/uploads/....mp4",
*                    "durationSeconds": 480, "summary": "..." } ] }
*   - lessonId: 이미 저장된 영상을 고칠 때만. 새 영상은 생략
*   - 개수(1~3, TOO_MANY_LESSONS·NO_LESSON)·요약글 길이(SUMMARY_TOO_LONG)는 명세서 오류 코드대로 서비스가 검사한다
*  */
public record LessonSaveDTO(
        @NotNull(message = "영상을 1개 이상 등록해 주세요") List<@Valid @NotNull LessonItem> lessons
) {

    public record LessonItem(
            Long lessonId,
            @NotNull(message = "영상 순서를 입력해 주세요") Integer sortOrder,
            @NotBlank(message = "영상 제목을 입력해 주세요")
            @Size(max = 100, message = "영상 제목은 100자까지 쓸 수 있어요") String title,
            @NotBlank(message = "영상 파일을 올려 주세요")
            @Size(max = 500, message = "영상 주소가 너무 길어요") String videoUrl,
            @NotNull(message = "영상 길이를 입력해 주세요")
            @Min(value = 1, message = "영상 길이를 확인해 주세요") Integer durationSeconds,
            String summary
    ) {
    }
}
