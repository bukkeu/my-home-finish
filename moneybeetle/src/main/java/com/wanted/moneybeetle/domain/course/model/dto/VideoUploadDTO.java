package com.wanted.moneybeetle.domain.course.model.dto;

/* comment.
*   담당 : DBA ① (강의·퀴즈)
*   API 78 부속 "영상 파일 올리기" 응답 { "videoUrl": "/uploads/....mp4" }
*   - 만료되지 않는 파일 키. 이 값을 그대로 PUT /lessons 요청에 담는다
*  */
public record VideoUploadDTO(String videoUrl) {
}
